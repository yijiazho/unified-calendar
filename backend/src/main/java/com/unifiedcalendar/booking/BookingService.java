package com.unifiedcalendar.booking;

import com.unifiedcalendar.auth.Admin;
import com.unifiedcalendar.auth.AdminRepository;
import com.unifiedcalendar.availability.AvailabilityService;
import com.unifiedcalendar.calendar.CalendarAccount;
import com.unifiedcalendar.calendar.CalendarAccountRepository;
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

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final AdminRepository adminRepository;
    private final CalendarAccountRepository calendarAccountRepository;
    private final BookingRepository bookingRepository;
    private final SlotReservationRepository slotReservationRepository;
    private final AvailabilityService availabilityService;
    private final GoogleTokenRefresher googleTokenRefresher;
    private final OutlookTokenRefresher outlookTokenRefresher;
    private final List<ProviderEventService> providerEventServices;
    private final EmailService emailService;
    private final BookingPersistenceService bookingPersistenceService;

    public BookingService(
            AdminRepository adminRepository,
            CalendarAccountRepository calendarAccountRepository,
            BookingRepository bookingRepository,
            SlotReservationRepository slotReservationRepository,
            AvailabilityService availabilityService,
            GoogleTokenRefresher googleTokenRefresher,
            OutlookTokenRefresher outlookTokenRefresher,
            List<ProviderEventService> providerEventServices,
            EmailService emailService,
            BookingPersistenceService bookingPersistenceService) {
        this.adminRepository = adminRepository;
        this.calendarAccountRepository = calendarAccountRepository;
        this.bookingRepository = bookingRepository;
        this.slotReservationRepository = slotReservationRepository;
        this.availabilityService = availabilityService;
        this.googleTokenRefresher = googleTokenRefresher;
        this.outlookTokenRefresher = outlookTokenRefresher;
        this.providerEventServices = providerEventServices;
        this.emailService = emailService;
        this.bookingPersistenceService = bookingPersistenceService;
    }

    /**
     * Orchestrates booking creation: validates slot, reserves atomically, creates provider event, then persists.
     * Does NOT hold DB connection during provider API calls (which can be slow).
     * Returns fire-and-forget confirmation emails asynchronously.
     */
    public BookingResponse createBooking(CreateBookingRequest request) {
        // 1. Resolve and validate request (no DB connection held during network calls)
        Admin admin = resolveAdmin(request.slug());
        Instant slotStart = parseInstant(request.slotStart(), "slotStart");
        Instant slotEnd = parseInstant(request.slotEnd(), "slotEnd");
        validateSlotDuration(slotStart, slotEnd);

        // 2. Resolve the primary calendar before reserving anything. A missing primary
        // is an admin configuration problem and must never leave an orphan reservation.
        CalendarAccount primary = findPrimaryCalendar(admin.id());

        // 3. Check SQLite cache for conflicts
        if (!availabilityService.isSlotAvailable(admin.id(), slotStart, slotEnd)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This time slot is no longer available.");
        }

        // 4. Atomically reserve the slot (fails with 409 if already reserved)
        SlotReservation reservation;
        try {
            reservation = slotReservationRepository.reserve(admin.id(), slotStart, slotEnd);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This time slot is no longer available.");
        }

        // Track whether reservation has been released; always cleanup on exit if not
        boolean reservationReleased = false;
        try {
            // 5. Refresh token once and perform live conflict check (outside transaction!)
            TokenRefreshResult refreshResult = refreshToken(primary);
            ProviderEventService providerService = findProviderService(primary.provider());
            validateNoLiveConflict(primary, providerService, refreshResult.accessToken(), slotStart, slotEnd);

            // 6. Create provider event (outside transaction!)
            String title = "Meeting with " + request.visitorName();
            String description = buildDescription(request);
            String providerEventId;
            try {
                providerEventId = providerService.createEvent(
                        primary, refreshResult.accessToken(), title, description, slotStart, slotEnd);
            } catch (Exception eventCreationEx) {
                log.error("Provider event creation failed for admin {}: {}", admin.id(), eventCreationEx.getMessage());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Booking creation failed");
            }

            // 7. Persist to DB atomically; roll back provider event on failure
            try {
                // Use the refreshed account from step 5 to avoid a second (potentially stale) refresh
                CalendarAccount accountWithRefreshedToken = refreshResult.updatedAccount();
                Booking saved = bookingPersistenceService.persistBooking(
                        admin, accountWithRefreshedToken, providerEventId, title,
                        slotStart, slotEnd,
                        request.visitorName(), request.visitorEmail(), request.visitorPhone(), request.notes());
                
                // Slot reservation is now implicit in the booking; delete the reservation row
                slotReservationRepository.delete(reservation.id());
                reservationReleased = true;
                
                // Step 8: Schedule confirmation emails separately (outside DB transaction)
                // If scheduling fails, we log but do NOT rollback the booking
                scheduleConfirmationEmails(saved);
                
                return new BookingResponse(
                        saved.id(), saved.visitorName(), slotStart, slotEnd,
                        admin.slug(), saved.cancelToken(), saved.rescheduleToken());
            } catch (Exception dbEx) {
                log.error("DB insert failed after creating provider event {} for admin {}: {}",
                        providerEventId, admin.id(), dbEx.getMessage(), dbEx);
                try {
                    // Rollback: delete provider event since DB write failed
                    providerService.deleteEvent(primary, refreshResult.accessToken(), providerEventId);
                    log.info("Rolled back provider event {} for admin {}", providerEventId, admin.id());
                } catch (Exception deleteEx) {
                    log.error("Orphaned provider event {} for admin {} — manual cleanup required: {}",
                            providerEventId, admin.id(), deleteEx.getMessage());
                }
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Booking creation failed");
            }
        } finally {
            // Release reservation if not already released (on any failure or non-standard exit)
            if (!reservationReleased) {
                slotReservationRepository.delete(reservation.id());
            }
        }
    }

    /**
     * Schedules confirmation emails asynchronously.
     * Logs failures without affecting the confirmed booking state.
     */
    private void scheduleConfirmationEmails(Booking booking) {
        try {
            emailService.sendBookingEmails(booking);
        } catch (Exception emailEx) {
            log.warn("Failed to schedule confirmation emails for booking {}: {}", 
                    booking.id(), emailEx.getMessage());
            // Do NOT re-throw; booking is already confirmed in the database
        }
    }

    private Admin resolveAdmin(String slug) {
        return adminRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin not found"));
    }

    private void validateSlotDuration(Instant slotStart, Instant slotEnd) {
        if (!slotEnd.equals(slotStart.plus(30, ChronoUnit.MINUTES))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "slotEnd must be exactly 30 minutes after slotStart");
        }
    }

    private CalendarAccount findPrimaryCalendar(Long adminId) {
        return calendarAccountRepository.findAllByAdminId(adminId)
                .stream()
                .filter(CalendarAccount::isPrimary)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "No primary calendar is configured. The calendar owner must select one before bookings can be created."));
    }

    private void validateNoLiveConflict(CalendarAccount primary, ProviderEventService providerService,
                                        String accessToken, Instant slotStart, Instant slotEnd) {
        if (providerService.hasConflict(primary, accessToken, slotStart, slotEnd)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This time slot is no longer available.");
        }
    }

    /**
     * Resolves a booking by its cancel or reschedule token; used by cancel and reschedule flows.
     * Returns the booking, or throws 404 if the token is unknown.
     */
    public Booking findByToken(String token, TokenType type) {
        return switch (type) {
            case CANCEL      -> bookingRepository.findByCancelToken(token)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
            case RESCHEDULE  -> bookingRepository.findByRescheduleToken(token)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        };
    }

    private Instant parseInstant(String value, String fieldName) {
        try {
            // Accepts any ISO-8601 timestamp (with or without offset); Instant.parse() converts to UTC.
            // Examples: "2024-03-15T14:00:00Z", "2024-03-15T14:00:00+02:00", "2024-03-15T14:00:00"
            return Instant.parse(value);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    fieldName + " must be a valid ISO-8601 timestamp (e.g., 2024-03-15T14:00:00Z or 2024-03-15T14:00:00+02:00)");
        }
    }

    private String buildDescription(CreateBookingRequest request) {
        StringBuilder sb = new StringBuilder();
        if (request.visitorPhone() != null && !request.visitorPhone().isBlank()) {
            sb.append("Phone: ").append(request.visitorPhone()).append("\n");
        }
        if (request.notes() != null && !request.notes().isBlank()) {
            sb.append("Notes: ").append(request.notes());
        }
        return sb.toString();
    }

    private TokenRefreshResult refreshToken(CalendarAccount account) {
        return switch (account.provider()) {
            case GOOGLE  -> googleTokenRefresher.refreshAccessToken(account);
            case OUTLOOK -> outlookTokenRefresher.refreshAccessToken(account);
        };
    }

    private ProviderEventService findProviderService(Provider provider) {
        return providerEventServices.stream()
                .filter(s -> s.supports(provider))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No ProviderEventService for: " + provider));
    }
}
