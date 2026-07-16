package com.unifiedcalendar.booking;

import com.unifiedcalendar.auth.Admin;
import com.unifiedcalendar.auth.AdminRepository;
import com.unifiedcalendar.availability.AvailabilityService;
import com.unifiedcalendar.calendar.CalendarAccount;
import com.unifiedcalendar.calendar.CalendarAccountRepository;
import com.unifiedcalendar.calendar.CalendarEventRepository;
import com.unifiedcalendar.calendar.GoogleTokenRefresher;
import com.unifiedcalendar.calendar.OutlookTokenRefresher;
import com.unifiedcalendar.calendar.Provider;
import com.unifiedcalendar.calendar.ProviderEventService;
import com.unifiedcalendar.calendar.TokenRefreshResult;
import com.unifiedcalendar.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookingService unit tests")
class BookingServiceTest {

    @Mock private AdminRepository adminRepository;
    @Mock private CalendarAccountRepository calendarAccountRepository;
    @Mock private CalendarEventRepository calendarEventRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private AvailabilityService availabilityService;
    @Mock private GoogleTokenRefresher googleTokenRefresher;
    @Mock private OutlookTokenRefresher outlookTokenRefresher;
    @Mock private ProviderEventService providerEventService;
    @Mock private EmailService emailService;

    private BookingService bookingService;

    private static final Instant SLOT_START = Instant.parse("2099-06-01T14:00:00Z");
    private static final Instant SLOT_END   = SLOT_START.plus(30, ChronoUnit.MINUTES);

    private Admin admin;
    private CalendarAccount primaryAccount;
    private CreateBookingRequest validRequest;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(
                adminRepository, calendarAccountRepository, calendarEventRepository,
                bookingRepository, availabilityService, googleTokenRefresher,
                outlookTokenRefresher, List.of(providerEventService), emailService);

        admin = new Admin(1L, "admin@example.com", "hash", "test-admin", "UTC", Instant.now(), Instant.now());
        primaryAccount = new CalendarAccount(
                10L, 1L, Provider.GOOGLE, "google-sub", "admin@gmail.com",
                "enc-access", "enc-refresh", true, Instant.now(), null, null);

        validRequest = new CreateBookingRequest(
                "test-admin", SLOT_START.toString(), SLOT_END.toString(),
                "John Doe", "john@example.com", "+1-555-0100", "First meeting");

        lenient().when(providerEventService.supports(Provider.GOOGLE)).thenReturn(true);
    }

    @Test
    @DisplayName("creates booking and returns response for valid request")
    void createsBookingSuccessfully() {
        when(adminRepository.findBySlug("test-admin")).thenReturn(Optional.of(admin));
        when(availabilityService.isSlotAvailable(1L, SLOT_START, SLOT_END)).thenReturn(true);
        when(calendarAccountRepository.findAllByAdminId(1L)).thenReturn(List.of(primaryAccount));
        when(googleTokenRefresher.refreshAccessToken(primaryAccount))
                .thenReturn(new TokenRefreshResult("plain-token", primaryAccount));
        when(calendarAccountRepository.save(any())).thenReturn(primaryAccount);
        when(providerEventService.hasConflict(any(), anyString(), any(), any())).thenReturn(false);
        when(providerEventService.createEvent(any(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn("google-event-id-123");
        when(calendarEventRepository.insertBookingEvent(any())).thenReturn(99L);
        Booking savedBooking = new Booking(42L, 1L, 99L, "John Doe", "john@example.com",
                "+1-555-0100", "First meeting", "CONFIRMED", "cancel-uuid", "reschedule-uuid", Instant.now());
        when(bookingRepository.save(any())).thenReturn(savedBooking);

        BookingResponse response = bookingService.createBooking(validRequest);

        assertThat(response.bookingId()).isEqualTo(42L);
        assertThat(response.visitorName()).isEqualTo("John Doe");
        assertThat(response.slotStart()).isEqualTo(SLOT_START);
        assertThat(response.slotEnd()).isEqualTo(SLOT_END);
        assertThat(response.adminName()).isEqualTo("test-admin");
        assertThat(response.cancelToken()).isEqualTo("cancel-uuid");
        assertThat(response.rescheduleToken()).isEqualTo("reschedule-uuid");

        verify(emailService).sendBookingEmails(savedBooking);
    }

    @Test
    @DisplayName("returns 404 when slug does not resolve to an admin")
    void returns404ForUnknownSlug() {
        when(adminRepository.findBySlug("test-admin")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.createBooking(validRequest))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Admin not found");
    }

    @Test
    @DisplayName("returns 409 when SQLite availability check fails")
    void returns409WhenSlotUnavailableInCache() {
        when(adminRepository.findBySlug("test-admin")).thenReturn(Optional.of(admin));
        when(availabilityService.isSlotAvailable(1L, SLOT_START, SLOT_END)).thenReturn(false);

        assertThatThrownBy(() -> bookingService.createBooking(validRequest))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(409);
    }

    @Test
    @DisplayName("returns 409 when live provider check finds a conflict the cache missed")
    void returns409WhenLiveProviderConflictFound() {
        when(adminRepository.findBySlug("test-admin")).thenReturn(Optional.of(admin));
        when(availabilityService.isSlotAvailable(1L, SLOT_START, SLOT_END)).thenReturn(true);
        when(calendarAccountRepository.findAllByAdminId(1L)).thenReturn(List.of(primaryAccount));
        when(googleTokenRefresher.refreshAccessToken(primaryAccount))
                .thenReturn(new TokenRefreshResult("plain-token", primaryAccount));
        when(calendarAccountRepository.save(any())).thenReturn(primaryAccount);
        when(providerEventService.hasConflict(any(), anyString(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> bookingService.createBooking(validRequest))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(409);

        verify(providerEventService, never()).createEvent(any(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("returns 400 when slotEnd is not 30 minutes after slotStart")
    void returns400ForInvalidSlotDuration() {
        CreateBookingRequest badRequest = new CreateBookingRequest(
                "test-admin", SLOT_START.toString(),
                SLOT_START.plus(45, ChronoUnit.MINUTES).toString(),
                "John Doe", "john@example.com", null, null);
        when(adminRepository.findBySlug("test-admin")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> bookingService.createBooking(badRequest))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(400);
    }

    @Test
    @DisplayName("rolls back provider event and returns 500 when DB insert fails")
    void rollsBackProviderEventOnDbFailure() {
        when(adminRepository.findBySlug("test-admin")).thenReturn(Optional.of(admin));
        when(availabilityService.isSlotAvailable(1L, SLOT_START, SLOT_END)).thenReturn(true);
        when(calendarAccountRepository.findAllByAdminId(1L)).thenReturn(List.of(primaryAccount));
        when(googleTokenRefresher.refreshAccessToken(primaryAccount))
                .thenReturn(new TokenRefreshResult("plain-token", primaryAccount));
        when(calendarAccountRepository.save(any())).thenReturn(primaryAccount);
        when(providerEventService.hasConflict(any(), anyString(), any(), any())).thenReturn(false);
        when(providerEventService.createEvent(any(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn("google-event-id-to-rollback");
        when(calendarEventRepository.insertBookingEvent(any()))
                .thenThrow(new RuntimeException("DB connection lost"));

        assertThatThrownBy(() -> bookingService.createBooking(validRequest))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(500);

        verify(providerEventService).deleteEvent(eq(primaryAccount), eq("plain-token"), eq("google-event-id-to-rollback"));
    }

    @Test
    @DisplayName("findByToken returns booking for valid cancel token")
    void findByTokenResolvesValidCancelToken() {
        Booking booking = new Booking(1L, 1L, 99L, "Jane", "jane@example.com",
                null, null, "CONFIRMED", "cancel-tok", "reschedule-tok", Instant.now());
        when(bookingRepository.findByCancelToken("cancel-tok")).thenReturn(Optional.of(booking));

        Booking result = bookingService.findByToken("cancel-tok", TokenType.CANCEL);
        assertThat(result).isEqualTo(booking);
    }

    @Test
    @DisplayName("findByToken throws 404 for unknown reschedule token")
    void findByTokenThrows404ForUnknownRescheduleToken() {
        when(bookingRepository.findByRescheduleToken("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.findByToken("bad-token", TokenType.RESCHEDULE))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(404);
    }
}
