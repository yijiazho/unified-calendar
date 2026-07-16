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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final AdminRepository adminRepository;
    private final CalendarAccountRepository calendarAccountRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final BookingRepository bookingRepository;
    private final AvailabilityService availabilityService;
    private final GoogleTokenRefresher googleTokenRefresher;
    private final OutlookTokenRefresher outlookTokenRefresher;
    private final List<ProviderEventService> providerEventServices;
    private final EmailService emailService;

    public BookingService(
            AdminRepository adminRepository,
            CalendarAccountRepository calendarAccountRepository,
            CalendarEventRepository calendarEventRepository,
            BookingRepository bookingRepository,
            AvailabilityService availabilityService,
            GoogleTokenRefresher googleTokenRefresher,
            OutlookTokenRefresher outlookTokenRefresher,
            List<ProviderEventService> providerEventServices,
            EmailService emailService) {
        this.adminRepository = adminRepository;
        this.calendarAccountRepository = calendarAccountRepository;
        this.calendarEventRepository = calendarEventRepository;
        this.bookingRepository = bookingRepository;
        this.availabilityService = availabilityService;
        this.googleTokenRefresher = googleTokenRefresher;
        this.outlookTokenRefresher = outlookTokenRefresher;
        this.providerEventServices = providerEventServices;
        this.emailService = emailService;
    }

    /**
     * Validates the slot twice (SQLite cache + live provider), creates the provider event and
     * persists the booking atomically, then triggers confirmation emails asynchronously.
     */
    @Transactional
    public BookingResponse createBooking(CreateBookingRequest request) {
        // Resolve admin
        Admin admin = adminRepository.findBySlug(request.slug())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin not found"));

        // Parse and validate slot times
        Instant slotStart = parseInstant(request.slotStart(), "slotStart");
        Instant slotEnd   = parseInstant(request.slotEnd(),   "slotEnd");
        if (!slotEnd.equals(slotStart.plus(30, ChronoUnit.MINUTES))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "slotEnd must be exactly 30 minutes after slotStart");
        }

        // SQLite availability check
        if (!availabilityService.isSlotAvailable(admin.id(), slotStart, slotEnd)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This time slot is no longer available.");
        }

        // Find primary calendar account
        CalendarAccount primary = calendarAccountRepository.findAllByAdminId(admin.id())
                .stream()
                .filter(CalendarAccount::isPrimary)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "This time slot is no longer available."));

        // Refresh access token
        TokenRefreshResult refreshResult = refreshToken(primary);
        calendarAccountRepository.save(refreshResult.updatedAccount());

        // Live provider conflict check
        ProviderEventService providerService = findProviderService(primary.provider());
        if (providerService.hasConflict(primary, refreshResult.accessToken(), slotStart, slotEnd)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This time slot is no longer available.");
        }

        // Create event in primary provider calendar
        String title       = "Meeting with " + request.visitorName();
        String description = buildDescription(request);
        String providerEventId = providerService.createEvent(
                primary, refreshResult.accessToken(), title, description, slotStart, slotEnd);

        // Persist calendar_event and booking rows atomically; roll back provider event on DB failure
        try {
            CalendarEvent calEvent = new CalendarEvent(
                    null, admin.id(), primary.id(), primary.provider(),
                    providerEventId, title, slotStart, slotEnd, true, Instant.now(), Instant.now());
            Long calEventId = calendarEventRepository.insertBookingEvent(calEvent);

            Booking booking = new Booking(
                    null, admin.id(), calEventId,
                    request.visitorName(), request.visitorEmail(),
                    request.visitorPhone(), request.notes(),
                    "CONFIRMED",
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    Instant.now());
            Booking saved = bookingRepository.save(booking);

            // Fire-and-forget email; runs after transaction commits in a separate thread
            emailService.sendBookingEmails(saved);

            return new BookingResponse(
                    saved.id(), saved.visitorName(), slotStart, slotEnd,
                    admin.slug(), saved.cancelToken(), saved.rescheduleToken());

        } catch (Exception dbEx) {
            log.error("DB insert failed after creating provider event {} for admin {}: {}",
                    providerEventId, admin.id(), dbEx.getMessage(), dbEx);
            // Best-effort rollback of the provider event
            try {
                providerService.deleteEvent(primary, refreshResult.accessToken(), providerEventId);
                log.info("Rolled back provider event {} for admin {}", providerEventId, admin.id());
            } catch (Exception deleteEx) {
                log.error("Orphaned provider event {} for admin {} — manual cleanup required: {}",
                        providerEventId, admin.id(), deleteEx.getMessage());
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Booking creation failed");
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
            return Instant.parse(value);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    fieldName + " must be a valid ISO-8601 UTC timestamp");
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
