package com.unifiedcalendar.booking;

import com.unifiedcalendar.auth.Admin;
import com.unifiedcalendar.auth.AdminRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static com.unifiedcalendar.booking.CancellationConflictException.Code.ALREADY_CANCELLED;
import static com.unifiedcalendar.booking.CancellationConflictException.Code.ALREADY_RESCHEDULED;

@Service
public class CancellationService {

    private static final Logger log = LoggerFactory.getLogger(CancellationService.class);

    private final BookingRepository bookingRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final CalendarAccountRepository calendarAccountRepository;
    private final AdminRepository adminRepository;
    private final GoogleTokenRefresher googleTokenRefresher;
    private final OutlookTokenRefresher outlookTokenRefresher;
    private final List<ProviderEventService> providerEventServices;
    private final CancellationPersistenceService persistenceService;
    private final EmailService emailService;

    public CancellationService(
            BookingRepository bookingRepository,
            CalendarEventRepository calendarEventRepository,
            CalendarAccountRepository calendarAccountRepository,
            AdminRepository adminRepository,
            GoogleTokenRefresher googleTokenRefresher,
            OutlookTokenRefresher outlookTokenRefresher,
            List<ProviderEventService> providerEventServices,
            CancellationPersistenceService persistenceService,
            EmailService emailService) {
        this.bookingRepository = bookingRepository;
        this.calendarEventRepository = calendarEventRepository;
        this.calendarAccountRepository = calendarAccountRepository;
        this.adminRepository = adminRepository;
        this.googleTokenRefresher = googleTokenRefresher;
        this.outlookTokenRefresher = outlookTokenRefresher;
        this.providerEventServices = providerEventServices;
        this.persistenceService = persistenceService;
        this.emailService = emailService;
    }

    /** Orchestrates provider deletion outside the transaction, then atomically updates local state. */
    public CancellationResponse cancel(String cancelToken) {
        Booking booking = bookingRepository.findByCancelToken(cancelToken)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Booking not found."));
        validateStatus(booking);

        CalendarEvent event = loadEvent(booking);
        if (event.startTimeUtc().isBefore(Instant.now())) {
            throw error(HttpStatus.GONE, "This appointment is in the past and cannot be cancelled.");
        }

        Admin admin = adminRepository.findById(booking.adminId())
                .orElseThrow(() -> new IllegalStateException("Booking admin not found: " + booking.adminId()));
        CalendarAccount account = calendarAccountRepository
                .findById(event.calendarAccountId(), booking.adminId())
                .orElseThrow(() -> new IllegalStateException(
                        "Booking calendar account not found: " + event.calendarAccountId()));

        deleteProviderEventBestEffort(account, event);

        if (!persistenceService.persistCancellation(booking)) {
            Booking current = bookingRepository.findByCancelToken(cancelToken).orElse(booking);
            validateStatus(current);
            throw conflict(ALREADY_CANCELLED, "This appointment has already been cancelled.");
        }

        Booking cancelled = withStatus(booking, "CANCELLED");
        scheduleCancellationEmails(cancelled, admin, event.startTimeUtc());
        return new CancellationResponse(
                "Appointment cancelled successfully.",
                event.startTimeUtc(),
                event.endTimeUtc());
    }

    private void validateStatus(Booking booking) {
        if ("CANCELLED".equals(booking.status())) {
            throw conflict(ALREADY_CANCELLED, "This appointment has already been cancelled.");
        }
        if ("RESCHEDULED".equals(booking.status())) {
            throw conflict(ALREADY_RESCHEDULED,
                    "This appointment has been rescheduled. Use your reschedule confirmation email to manage it.");
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

    private void deleteProviderEventBestEffort(CalendarAccount account, CalendarEvent event) {
        TokenRefreshResult refresh;
        try {
            refresh = refreshToken(account);
        } catch (Exception ex) {
            logProviderDeletionFailure(event, ex);
            return;
        }

        try {
            calendarAccountRepository.save(refresh.updatedAccount());
        } catch (Exception ex) {
            log.error("Refreshed credentials for calendar account {} could not be persisted; "
                            + "continuing with provider deletion: {}",
                    account.id(), ex.getMessage(), ex);
        }

        try {
            findProviderService(account.provider()).deleteEvent(
                    refresh.updatedAccount(), refresh.accessToken(), event.providerEventId());
        } catch (Exception ex) {
            logProviderDeletionFailure(event, ex);
        }
    }

    private void logProviderDeletionFailure(CalendarEvent event, Exception ex) {
        log.error("Provider event {} could not be deleted for booking calendar event {}; "
                        + "continuing with local cancellation: {}",
                event.providerEventId(), event.id(), ex.getMessage(), ex);
    }

    private void scheduleCancellationEmails(Booking booking, Admin admin, Instant cancelledStart) {
        try {
            emailService.sendCancellationEmails(booking, admin, cancelledStart);
        } catch (Exception ex) {
            log.warn("Failed to schedule cancellation emails for booking {}: {}",
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
                .orElseThrow(() -> new IllegalStateException("No ProviderEventService for: " + provider));
    }

    private Booking withStatus(Booking booking, String status) {
        return new Booking(
                booking.id(), booking.adminId(), booking.calendarEventId(),
                booking.visitorName(), booking.visitorEmail(), booking.visitorPhone(), booking.notes(),
                status, booking.cancelToken(), booking.rescheduleToken(), booking.createdAt());
    }

    private ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }

    private CancellationConflictException conflict(CancellationConflictException.Code code, String message) {
        return new CancellationConflictException(code, message);
    }
}
