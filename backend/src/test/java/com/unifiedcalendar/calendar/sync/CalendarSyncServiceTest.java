package com.unifiedcalendar.calendar.sync;

import com.unifiedcalendar.calendar.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("CalendarSyncService")
class CalendarSyncServiceTest {

    private CalendarAccountRepository calendarAccountRepository;
    private CalendarEventRepository calendarEventRepository;
    private GoogleSyncAdapter googleSyncAdapter;
    private OutlookSyncAdapter outlookSyncAdapter;
    private GoogleTokenRefresher googleTokenRefresher;
    private OutlookTokenRefresher outlookTokenRefresher;
    private CalendarSyncService service;

    @BeforeEach
    void setUp() {
        calendarAccountRepository = mock(CalendarAccountRepository.class);
        calendarEventRepository   = mock(CalendarEventRepository.class);
        googleSyncAdapter         = mock(GoogleSyncAdapter.class);
        outlookSyncAdapter        = mock(OutlookSyncAdapter.class);
        googleTokenRefresher      = mock(GoogleTokenRefresher.class);
        outlookTokenRefresher     = mock(OutlookTokenRefresher.class);

        service = new CalendarSyncService(
                calendarAccountRepository, calendarEventRepository,
                googleSyncAdapter, outlookSyncAdapter,
                googleTokenRefresher, outlookTokenRefresher);
    }

    private static CalendarAccount account(Long id, Long adminId, String provider) {
        return new CalendarAccount(id, adminId, provider, "sub-" + id,
                "user@example.com", "enc_access", "enc_refresh",
                false, Instant.parse("2024-01-01T00:00:00Z"), null);
    }

    private static CalendarEvent event(Long accountId, Long adminId, String provider, String eventId) {
        return new CalendarEvent(null, adminId, accountId, provider, eventId,
                "Meeting",
                Instant.parse("2024-06-01T09:00:00Z"), Instant.parse("2024-06-01T10:00:00Z"),
                false, null, null);
    }

    @Test
    @DisplayName("syncAll refreshes token, upserts events, prunes stale ids, updates last_sync_at")
    void syncAllHappyPath() {
        CalendarAccount acc = account(1L, 10L, "GOOGLE");
        CalendarEvent evt   = event(1L, 10L, "GOOGLE", "evt-001");

        when(calendarAccountRepository.findAll()).thenReturn(List.of(acc));
        when(googleTokenRefresher.refreshAccessToken(acc)).thenReturn("fresh-token");
        when(googleSyncAdapter.fetchEvents(eq(acc), eq("fresh-token"), any(), any()))
                .thenReturn(List.of(evt));

        service.syncAll();

        verify(calendarEventRepository).upsert(evt);
        verify(calendarEventRepository)
                .deleteByCalendarAccountIdAndProviderEventIdNotIn(1L, List.of("evt-001"));
        verify(calendarAccountRepository).updateLastSyncAt(eq(1L), argThat(t -> t != null));
    }

    @Test
    @DisplayName("syncAll with no accounts completes without touching event or adapter")
    void syncAllNoAccounts() {
        when(calendarAccountRepository.findAll()).thenReturn(List.of());

        service.syncAll();

        verifyNoInteractions(calendarEventRepository, googleSyncAdapter, outlookSyncAdapter);
    }

    @Test
    @DisplayName("token refresh failure marks account with null lastSyncAt and skips its sync")
    void tokenRefreshFailureMarksAccountAndContinues() {
        CalendarAccount failAcc = account(1L, 10L, "GOOGLE");
        CalendarAccount goodAcc = account(2L, 20L, "OUTLOOK");

        when(calendarAccountRepository.findAll()).thenReturn(List.of(failAcc, goodAcc));
        when(googleTokenRefresher.refreshAccessToken(failAcc))
                .thenThrow(new RuntimeException("token revoked"));
        when(outlookTokenRefresher.refreshAccessToken(goodAcc)).thenReturn("ms-token");
        when(outlookSyncAdapter.fetchEvents(eq(goodAcc), eq("ms-token"), any(), any()))
                .thenReturn(List.of());

        service.syncAll();

        // Failed account gets null lastSyncAt via updateLastSyncAt
        verify(calendarAccountRepository).updateLastSyncAt(eq(1L), isNull());
        // Good account still synced and gets a non-null timestamp
        verify(outlookSyncAdapter).fetchEvents(eq(goodAcc), eq("ms-token"), any(), any());
        verify(calendarAccountRepository).updateLastSyncAt(eq(2L), argThat(t -> t != null));
    }

    @Test
    @DisplayName("provider API error skips that account but continues syncing others")
    void providerApiErrorContinues() {
        CalendarAccount acc1 = account(1L, 10L, "GOOGLE");
        CalendarAccount acc2 = account(2L, 20L, "GOOGLE");
        CalendarEvent evt2   = event(2L, 20L, "GOOGLE", "evt-002");

        when(calendarAccountRepository.findAll()).thenReturn(List.of(acc1, acc2));
        when(googleTokenRefresher.refreshAccessToken(acc1)).thenReturn("tok1");
        when(googleTokenRefresher.refreshAccessToken(acc2)).thenReturn("tok2");
        when(googleSyncAdapter.fetchEvents(eq(acc1), eq("tok1"), any(), any()))
                .thenThrow(new RuntimeException("503 Service Unavailable"));
        when(googleSyncAdapter.fetchEvents(eq(acc2), eq("tok2"), any(), any()))
                .thenReturn(List.of(evt2));

        service.syncAll();

        // acc1 produced no upserts
        verify(calendarEventRepository, never())
                .upsert(argThat(e -> e.calendarAccountId().equals(1L)));
        // acc2 was still synced
        verify(calendarEventRepository).upsert(evt2);
    }

    @Test
    @DisplayName("syncAll with empty event list calls deleteNotIn with empty list (clears stale events)")
    void syncAllEmptyResponseClearsAllEvents() {
        CalendarAccount acc = account(1L, 10L, "GOOGLE");

        when(calendarAccountRepository.findAll()).thenReturn(List.of(acc));
        when(googleTokenRefresher.refreshAccessToken(acc)).thenReturn("token");
        when(googleSyncAdapter.fetchEvents(eq(acc), eq("token"), any(), any()))
                .thenReturn(List.of());

        service.syncAll();

        verify(calendarEventRepository)
                .deleteByCalendarAccountIdAndProviderEventIdNotIn(1L, List.of());
        verify(calendarEventRepository, never()).upsert(any());
    }
}
