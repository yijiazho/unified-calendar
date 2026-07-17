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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CancellationService")
class CancellationServiceTest {

    private static final Instant SLOT_START = Instant.parse("2099-06-02T09:00:00Z");
    private static final Instant SLOT_END = SLOT_START.plus(30, ChronoUnit.MINUTES);

    @Mock private BookingRepository bookingRepository;
    @Mock private CalendarEventRepository calendarEventRepository;
    @Mock private CalendarAccountRepository calendarAccountRepository;
    @Mock private AdminRepository adminRepository;
    @Mock private GoogleTokenRefresher googleTokenRefresher;
    @Mock private OutlookTokenRefresher outlookTokenRefresher;
    @Mock private ProviderEventService providerEventService;
    @Mock private CancellationPersistenceService persistenceService;
    @Mock private EmailService emailService;

    private CancellationService service;
    private Booking booking;
    private CalendarEvent event;
    private CalendarAccount account;
    private CalendarAccount rotatedAccount;

    @BeforeEach
    void setUp() {
        service = new CancellationService(
                bookingRepository, calendarEventRepository, calendarAccountRepository,
                adminRepository, googleTokenRefresher, outlookTokenRefresher,
                List.of(providerEventService), persistenceService, emailService);

        booking = new Booking(
                1L, 2L, 3L, "Visitor", "visitor@example.com", null, null,
                "CONFIRMED", "cancel-token", "reschedule-token", Instant.now());
        event = new CalendarEvent(
                3L, 2L, 4L, Provider.OUTLOOK, "provider-event", "Meeting",
                SLOT_START, SLOT_END, true, Instant.now(), Instant.now());
        account = new CalendarAccount(
                4L, 2L, Provider.OUTLOOK, "outlook-account", "admin@example.com",
                "old-access", "old-refresh", true, Instant.now(), null, null);
        rotatedAccount = new CalendarAccount(
                4L, 2L, Provider.OUTLOOK, "outlook-account", "admin@example.com",
                "new-access", "new-refresh", true, account.connectedAt(), null, null);

        when(bookingRepository.findByCancelToken("cancel-token")).thenReturn(Optional.of(booking));
        when(calendarEventRepository.findById(3L, 2L)).thenReturn(Optional.of(event));
        when(adminRepository.findById(2L)).thenReturn(Optional.of(
                new Admin(2L, "admin@example.com", "hash", "admin", "UTC", Instant.now(), Instant.now())));
        when(calendarAccountRepository.findById(4L, 2L)).thenReturn(Optional.of(account));
        when(outlookTokenRefresher.refreshAccessToken(account))
                .thenReturn(new TokenRefreshResult("plain-access", rotatedAccount));
        when(providerEventService.supports(Provider.OUTLOOK)).thenReturn(true);
        when(persistenceService.persistCancellation(booking)).thenReturn(true);
    }

    @Test
    @DisplayName("persists rotated provider credentials before deleting the event")
    void persistsRotatedCredentials() {
        CancellationResponse response = service.cancel("cancel-token");

        assertThat(response.slotStart()).isEqualTo(SLOT_START);
        verify(calendarAccountRepository).save(rotatedAccount);
        verify(providerEventService).deleteEvent(rotatedAccount, "plain-access", "provider-event");
    }

    @Test
    @DisplayName("credential persistence failure does not prevent provider or local cancellation")
    void credentialPersistenceFailureIsIsolated() {
        doThrow(new RuntimeException("database unavailable"))
                .when(calendarAccountRepository).save(rotatedAccount);

        CancellationResponse response = service.cancel("cancel-token");

        assertThat(response.message()).isEqualTo("Appointment cancelled successfully.");
        verify(providerEventService).deleteEvent(rotatedAccount, "plain-access", "provider-event");
        verify(persistenceService).persistCancellation(booking);
    }
}
