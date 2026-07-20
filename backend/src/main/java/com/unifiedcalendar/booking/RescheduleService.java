package com.unifiedcalendar.booking;

import com.unifiedcalendar.auth.Admin;
import com.unifiedcalendar.auth.AdminRepository;
import com.unifiedcalendar.availability.AvailabilityService;
import com.unifiedcalendar.calendar.CalendarAccount;
import com.unifiedcalendar.calendar.CalendarAccountRepository;
import com.unifiedcalendar.calendar.CalendarEvent;
import com.unifiedcalendar.calendar.CalendarEventRepository;
import com.unifiedcalendar.calendar.GoogleTokenRefresher;
import com.unifiedcalendar.calendar.OutlookTokenRefresher;
import com.unifiedcalendar.calendar.Provider;
import com.unifiedcalendar.calendar.ProviderEventService;
import com.unifiedcalendar.calendar.TokenRefreshResult;
import com.unifiedcalendar.email.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class RescheduleService {

    private static final Logger log = LoggerFactory.getLogger(RescheduleService.class);
    private static final int BOOKING_LOCK_STRIPES = 64;

    private final BookingRepository bookingRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final CalendarAccountRepository calendarAccountRepository;
    private final AdminRepository adminRepository;
    private final AvailabilityService availabilityService;
    private final SlotReservationRepository slotReservationRepository;
    private final GoogleTokenRefresher googleTokenRefresher;
    private final OutlookTokenRefresher outlookTokenRefresher;
    private final List<ProviderEventService> providerEventServices;
    private final ReschedulePersistenceService persistenceService;
    private final EmailService emailService;
    private final ReentrantLock[] bookingLocks = createBookingLocks();

    public RescheduleService(
            BookingRepository bookingRepository,
            CalendarEventRepository calendarEventRepository,
            CalendarAccountRepository calendarAccountRepository,
            AdminRepository adminRepository,
            AvailabilityService availabilityService,
            SlotReservationRepository slotReservationRepository,
            GoogleTokenRefresher googleTokenRefresher,
            OutlookTokenRefresher outlookTokenRefresher,
            List<ProviderEventService> providerEventServices,
            ReschedulePersistenceService persistenceService,
            EmailService emailService) {
        this.bookingRepository = bookingRepository;
        this.calendarEventRepository = calendarEventRepository;
        this.calendarAccountRepository = calendarAccountRepository;
        this.adminRepository = adminRepository;
        this.availabilityService = availabilityService;
        this.slotReservationRepository = slotReservationRepository;
        this.googleTokenRefresher = googleTokenRefresher;
        this.outlookTokenRefresher = outlookTokenRefresher;
        this.providerEventServices = providerEventServices;
        this.persistenceService = persistenceService;
        this.emailService = emailService;
    }

    /** Performs provider I/O outside a transaction, then atomically updates local state. */
    public RescheduleResponse reschedule(
            String rescheduleToken, Instant newSlotStart, Instant newSlotEnd) {
        ReentrantLock bookingLock = bookingLock(rescheduleToken);
        bookingLock.lock();
        try {
            return rescheduleLocked(rescheduleToken, newSlotStart, newSlotEnd);
        } finally {
            bookingLock.unlock();
        }
    }

    private RescheduleResponse rescheduleLocked(
            String rescheduleToken, Instant newSlotStart, Instant newSlotEnd) {
        Booking booking = bookingRepository.findByRescheduleToken(rescheduleToken)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Booking not found."));
        if ("CANCELLED".equals(booking.status())) {
            throw error(HttpStatus.CONFLICT, "This appointment has already been cancelled.");
        }

        CalendarEvent event = loadEvent(booking);
        if (event.startTimeUtc().isBefore(Instant.now())) {
            throw error(HttpStatus.GONE, "Cannot reschedule a past appointment.");
        }
        validateNewSlot(newSlotStart, newSlotEnd);

        if (!availabilityService.isSlotAvailable(
                booking.adminId(), newSlotStart, newSlotEnd)) {
            throw unavailable();
        }

        SlotReservation reservation;
        boolean releaseDestinationReservation = true;
        try {
            reservation = slotReservationRepository.reserve(
                    booking.adminId(), newSlotStart, newSlotEnd);
        } catch (DataIntegrityViolationException ex) {
            throw unavailable();
        }

        try {
            Admin admin = adminRepository.findById(booking.adminId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Booking admin not found: " + booking.adminId()));
            CalendarAccount account = calendarAccountRepository
                    .findById(event.calendarAccountId(), booking.adminId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Booking calendar account not found: " + event.calendarAccountId()));
            ProviderEventService providerService = findProviderService(account.provider());
            TokenRefreshResult refresh = refreshToken(account);
            persistRefreshedCredentials(booking, refresh.updatedAccount());

            if (providerService.hasConflict(
                    refresh.updatedAccount(), refresh.accessToken(), newSlotStart, newSlotEnd)) {
                throw unavailable();
            }

            updateProviderEvent(
                    booking, event, providerService, refresh, newSlotStart, newSlotEnd);

            try {
                persistenceService.persistReschedule(
                        booking, newSlotStart, newSlotEnd, reservation.id());
            } catch (Exception persistenceEx) {
                releaseDestinationReservation = compensateProviderUpdate(
                        booking, event, providerService, refresh, persistenceEx);
                throw error(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to update calendar event. Please try again.");
            }
            scheduleRescheduleEmails(booking, admin, newSlotStart, newSlotEnd);

            return new RescheduleResponse(
                    booking.id(), booking.visitorName(), newSlotStart, newSlotEnd,
                    booking.cancelToken(), booking.rescheduleToken());
        } finally {
            // Idempotent cleanup covers every pre-persistence failure and is harmless after
            // the transactional delete performed by persistReschedule.
            if (releaseDestinationReservation) {
                releaseReservation(reservation, booking);
            }
        }
    }

    private void updateProviderEvent(
            Booking booking, CalendarEvent event, ProviderEventService providerService,
            TokenRefreshResult refresh, Instant newStart, Instant newEnd) {
        try {
            providerService.updateEvent(
                    refresh.updatedAccount(), refresh.accessToken(), event.providerEventId(),
                    newStart, newEnd);
        } catch (Exception ex) {
            log.error("Failed to update provider event {} for booking {} and admin {} to [{}, {}): {}",
                    event.providerEventId(), booking.id(), booking.adminId(),
                    newStart, newEnd, ex.getMessage(), ex);
            throw error(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to update calendar event. Please try again.");
        }
    }

    private boolean compensateProviderUpdate(
            Booking booking, CalendarEvent event, ProviderEventService providerService,
            TokenRefreshResult refresh, Exception persistenceEx) {
        log.error("Local reschedule persistence failed for booking {} and provider event {}; "
                        + "restoring provider time to [{}, {}): {}",
                booking.id(), event.providerEventId(), event.startTimeUtc(), event.endTimeUtc(),
                persistenceEx.getMessage(), persistenceEx);
        try {
            providerService.updateEvent(
                    refresh.updatedAccount(), refresh.accessToken(), event.providerEventId(),
                    event.startTimeUtc(), event.endTimeUtc());
            return true;
        } catch (Exception compensationEx) {
            persistenceEx.addSuppressed(compensationEx);
            log.error("CRITICAL: provider rollback failed for booking {} and event {}; "
                            + "provider may be out of sync with SQLite. Destination reservation is retained "
                            + "to prevent a double-booking: {}",
                    booking.id(), event.providerEventId(), compensationEx.getMessage(), compensationEx);
            return false;
        }
    }

    private void releaseReservation(SlotReservation reservation, Booking booking) {
        try {
            slotReservationRepository.delete(reservation.id());
        } catch (Exception ex) {
            log.error("Failed to release slot reservation {} for booking {}: {}",
                    reservation.id(), booking.id(), ex.getMessage(), ex);
        }
    }

    private void persistRefreshedCredentials(Booking booking, CalendarAccount refreshedAccount) {
        try {
            calendarAccountRepository.save(refreshedAccount);
        } catch (Exception ex) {
            log.error("Failed to persist refreshed credentials for calendar account {} "
                            + "while rescheduling booking {}: {}",
                    refreshedAccount.id(), booking.id(), ex.getMessage(), ex);
            throw error(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to refresh calendar credentials. Please try again.");
        }
    }

    private void validateNewSlot(Instant start, Instant end) {
        if (!end.equals(start.plus(30, ChronoUnit.MINUTES))) {
            throw error(HttpStatus.BAD_REQUEST, "New slot must be exactly 30 minutes.");
        }
        if (!start.isAfter(Instant.now())) {
            throw error(HttpStatus.BAD_REQUEST, "New slot must be in the future.");
        }
    }

    private CalendarEvent loadEvent(Booking booking) {
        if (booking.calendarEventId() == null) {
            throw new IllegalStateException("Booking has no associated calendar event: " + booking.id());
        }
        return calendarEventRepository.findById(booking.calendarEventId(), booking.adminId())
                .orElseThrow(() -> new IllegalStateException(
                        "Booking calendar event not found: " + booking.calendarEventId()));
    }

    private void scheduleRescheduleEmails(
            Booking booking, Admin admin, Instant newStart, Instant newEnd) {
        try {
            emailService.sendRescheduleEmails(booking, admin, newStart, newEnd);
        } catch (Exception ex) {
            log.warn("Failed to schedule reschedule emails for booking {}: {}",
                    booking.id(), ex.getMessage());
        }
    }

    private TokenRefreshResult refreshToken(CalendarAccount account) {
        return switch (account.provider()) {
            case GOOGLE -> googleTokenRefresher.refreshAccessToken(account);
            case OUTLOOK -> outlookTokenRefresher.refreshAccessToken(account);
        };
    }

    private ProviderEventService findProviderService(Provider provider) {
        return providerEventServices.stream()
                .filter(service -> service.supports(provider))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No ProviderEventService for: " + provider));
    }

    private ResponseStatusException unavailable() {
        return error(HttpStatus.CONFLICT, "The selected time slot is no longer available.");
    }

    private ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }

    private ReentrantLock bookingLock(String rescheduleToken) {
        return bookingLocks[Math.floorMod(rescheduleToken.hashCode(), bookingLocks.length)];
    }

    private static ReentrantLock[] createBookingLocks() {
        ReentrantLock[] locks = new ReentrantLock[BOOKING_LOCK_STRIPES];
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new ReentrantLock();
        }
        return locks;
    }
}
