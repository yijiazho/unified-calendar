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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RescheduleService")
class RescheduleServiceTest {

    private static final Instant OLD_START = Instant.parse("2099-06-01T09:00:00Z");
    private static final Instant OLD_END = OLD_START.plus(30, ChronoUnit.MINUTES);
    private static final Instant NEW_START = Instant.parse("2099-06-02T10:00:00Z");
    private static final Instant NEW_END = NEW_START.plus(30, ChronoUnit.MINUTES);

    @Mock private BookingRepository bookingRepository;
    @Mock private CalendarEventRepository calendarEventRepository;
    @Mock private CalendarAccountRepository calendarAccountRepository;
    @Mock private AdminRepository adminRepository;
    @Mock private AvailabilityService availabilityService;
    @Mock private SlotReservationRepository slotReservationRepository;
    @Mock private GoogleTokenRefresher googleTokenRefresher;
    @Mock private OutlookTokenRefresher outlookTokenRefresher;
    @Mock private ProviderEventService providerEventService;
    @Mock private ReschedulePersistenceService persistenceService;
    @Mock private EmailService emailService;

    private RescheduleService service;
    private Booking booking;
    private CalendarEvent event;
    private CalendarAccount account;
    private SlotReservation reservation;

    @BeforeEach
    void setUp() {
        service = new RescheduleService(
                bookingRepository, calendarEventRepository, calendarAccountRepository,
                adminRepository, availabilityService, slotReservationRepository,
                googleTokenRefresher, outlookTokenRefresher, List.of(providerEventService),
                persistenceService, emailService);
        booking = new Booking(
                1L, 2L, 3L, "Visitor", "visitor@example.com", null, null,
                "CONFIRMED", "cancel-token", "reschedule-token", Instant.now());
        event = new CalendarEvent(
                3L, 2L, 4L, Provider.GOOGLE, "provider-event", "Meeting",
                OLD_START, OLD_END, true, Instant.now(), Instant.now());
        account = new CalendarAccount(
                4L, 2L, Provider.GOOGLE, "google-account", "admin@example.com",
                "access", "refresh", true, Instant.now(), null, null);
        reservation = new SlotReservation(5L, 2L, NEW_START, NEW_END, Instant.now());

        when(bookingRepository.findByRescheduleToken("reschedule-token"))
                .thenReturn(Optional.of(booking));
        when(calendarEventRepository.findById(3L, 2L)).thenReturn(Optional.of(event));
        when(availabilityService.isSlotAvailable(2L, NEW_START, NEW_END)).thenReturn(true);
        when(slotReservationRepository.reserve(2L, NEW_START, NEW_END)).thenReturn(reservation);
        when(adminRepository.findById(2L)).thenReturn(Optional.of(
                new Admin(2L, "admin@example.com", "hash", "admin", "UTC",
                        Instant.now(), Instant.now())));
        when(calendarAccountRepository.findById(4L, 2L)).thenReturn(Optional.of(account));
        when(providerEventService.supports(Provider.GOOGLE)).thenReturn(true);
        when(googleTokenRefresher.refreshAccessToken(account))
                .thenReturn(new TokenRefreshResult("plain-token", account));
    }

    @Test
    @DisplayName("persists refreshed credentials before a live conflict response")
    void persistsRefreshedCredentialsBeforeLiveConflict() {
        when(providerEventService.hasConflict(account, "plain-token", NEW_START, NEW_END))
                .thenReturn(true);

        assertThatThrownBy(() -> service.reschedule("reschedule-token", NEW_START, NEW_END))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(409);

        InOrder credentialAndProviderCalls = inOrder(
                calendarAccountRepository, providerEventService);
        credentialAndProviderCalls.verify(calendarAccountRepository).save(account);
        credentialAndProviderCalls.verify(providerEventService).hasConflict(
                account, "plain-token", NEW_START, NEW_END);
        verify(providerEventService, never())
                .updateEvent(any(), any(), any(), any(), any());
        verify(slotReservationRepository).delete(reservation.id());
    }

    @Test
    @DisplayName("keeps refreshed credentials when the provider update fails")
    void persistsRefreshedCredentialsBeforeProviderFailure() {
        org.mockito.Mockito.doThrow(new RuntimeException("provider unavailable"))
                .when(providerEventService)
                .updateEvent(account, "plain-token", "provider-event", NEW_START, NEW_END);

        assertThatThrownBy(() -> service.reschedule("reschedule-token", NEW_START, NEW_END))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(500);

        InOrder credentialAndProviderCalls = inOrder(
                calendarAccountRepository, providerEventService);
        credentialAndProviderCalls.verify(calendarAccountRepository).save(account);
        credentialAndProviderCalls.verify(providerEventService).hasConflict(
                account, "plain-token", NEW_START, NEW_END);
        credentialAndProviderCalls.verify(providerEventService).updateEvent(
                account, "plain-token", "provider-event", NEW_START, NEW_END);
        verify(slotReservationRepository).delete(reservation.id());
    }

    @Test
    @DisplayName("restores the provider's original time when local persistence fails")
    void compensatesProviderAfterPersistenceFailure() {
        RuntimeException persistenceFailure = new RuntimeException("database unavailable");
        org.mockito.Mockito.doThrow(persistenceFailure)
                .when(persistenceService)
                .persistReschedule(booking, NEW_START, NEW_END, reservation.id());

        assertThatThrownBy(() -> service.reschedule("reschedule-token", NEW_START, NEW_END))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(500);

        InOrder updates = inOrder(providerEventService);
        updates.verify(providerEventService).updateEvent(
                account, "plain-token", "provider-event", NEW_START, NEW_END);
        updates.verify(providerEventService).updateEvent(
                account, "plain-token", "provider-event", OLD_START, OLD_END);
        verify(slotReservationRepository).delete(reservation.id());
        verify(emailService, never()).sendRescheduleEmails(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("retains the destination reservation when provider compensation also fails")
    void retainsReservationAfterCompensationFailure() {
        org.mockito.Mockito.doThrow(new RuntimeException("database unavailable"))
                .when(persistenceService)
                .persistReschedule(booking, NEW_START, NEW_END, reservation.id());
        org.mockito.Mockito.doNothing()
                .doThrow(new RuntimeException("provider rollback unavailable"))
                .when(providerEventService)
                .updateEvent(any(), any(), any(), any(), any());

        assertThatThrownBy(() -> service.reschedule("reschedule-token", NEW_START, NEW_END))
                .isInstanceOf(ResponseStatusException.class);

        verify(providerEventService, times(2))
                .updateEvent(any(), any(), any(), any(), any());
        verify(slotReservationRepository, never()).delete(reservation.id());
    }

    @Test
    @DisplayName("serializes concurrent reschedules for the same booking")
    void serializesConcurrentReschedules() throws Exception {
        SlotReservation secondReservation = new SlotReservation(
                6L, 2L, NEW_START, NEW_END, Instant.now());
        when(slotReservationRepository.reserve(2L, NEW_START, NEW_END))
                .thenReturn(reservation, secondReservation);

        CountDownLatch firstProviderUpdateEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstProviderUpdate = new CountDownLatch(1);
        AtomicInteger updateCalls = new AtomicInteger();
        org.mockito.Mockito.doAnswer(invocation -> {
            if (updateCalls.incrementAndGet() == 1) {
                firstProviderUpdateEntered.countDown();
                if (!releaseFirstProviderUpdate.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("test timed out waiting to release provider update");
                }
            }
            return null;
        }).when(providerEventService).updateEvent(
                any(), any(), any(), any(), any());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<RescheduleResponse> first = executor.submit(
                    () -> service.reschedule("reschedule-token", NEW_START, NEW_END));
            assertThat(firstProviderUpdateEntered.await(5, TimeUnit.SECONDS)).isTrue();

            CountDownLatch secondStarted = new CountDownLatch(1);
            Future<RescheduleResponse> second = executor.submit(() -> {
                secondStarted.countDown();
                return service.reschedule("reschedule-token", NEW_START, NEW_END);
            });
            assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(second.isDone()).isFalse();
            verify(providerEventService, times(1))
                    .updateEvent(any(), any(), any(), any(), any());

            releaseFirstProviderUpdate.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isNotNull();
            assertThat(second.get(5, TimeUnit.SECONDS)).isNotNull();
            verify(providerEventService, times(2))
                    .updateEvent(any(), any(), any(), any(), any());
        } finally {
            releaseFirstProviderUpdate.countDown();
            executor.shutdownNow();
        }
    }
}
