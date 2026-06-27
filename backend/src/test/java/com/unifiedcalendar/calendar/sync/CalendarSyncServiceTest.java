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

        when(googleSyncAdapter.supports(Provider.GOOGLE)).thenReturn(true);
        when(outlookSyncAdapter.supports(Provider.OUTLOOK)).thenReturn(true);

        service = new CalendarSyncService(
                calendarAccountRepository, calendarEventRepository,
                googleTokenRefresher, outlookTokenRefresher,
                List.of(googleSyncAdapter, outlookSyncAdapter));
    }

    private static CalendarAccount account(Long id, Long adminId, Provider provider) {
        return new CalendarAccount(id, adminId, provider, "sub-" + id,
                "user@example.com", "enc_access", "enc_refresh",
                false, Instant.parse("2024-01-01T00:00:00Z"), null, null);
    }

    /** Builds a TokenRefreshResult wrapping the given plaintext token; updatedAccount uses same object for simplicity. */
    private static TokenRefreshResult tokenResult(String token, CalendarAccount account) {
        return new TokenRefreshResult(token, account);
    }

    private static CalendarEvent event(Long accountId, Long adminId, String providerEventId) {
        return new CalendarEvent(null, adminId, accountId, Provider.GOOGLE, providerEventId,
                "Meeting",
                Instant.parse("2024-06-01T09:00:00Z"), Instant.parse("2024-06-01T10:00:00Z"),
                false, null, null);
    }

    @Test
    @DisplayName("syncAll refreshes token, saves updated account, upserts events, prunes stale ids, updates last_sync_at")
    void syncAllHappyPath() {
        CalendarAccount acc = account(1L, 10L, Provider.GOOGLE);
        CalendarEvent evt   = event(1L, 10L, "evt-001");

        when(calendarAccountRepository.findAll()).thenReturn(List.of(acc));
        when(googleTokenRefresher.refreshAccessToken(acc)).thenReturn(tokenResult("fresh-token", acc));
        when(calendarAccountRepository.save(acc)).thenReturn(acc);
        when(googleSyncAdapter.fetchEvents(eq(acc), eq("fresh-token"), any(), any()))
                .thenReturn(List.of(evt));

        service.syncAll();

        verify(calendarAccountRepository).save(acc);
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
    @DisplayName("token refresh failure records error via markSyncFailed and skips that account's sync")
    void tokenRefreshFailureMarksAccountAndContinues() {
        CalendarAccount failAcc = account(1L, 10L, Provider.GOOGLE);
        CalendarAccount goodAcc = account(2L, 20L, Provider.OUTLOOK);

        when(calendarAccountRepository.findAll()).thenReturn(List.of(failAcc, goodAcc));
        when(googleTokenRefresher.refreshAccessToken(failAcc))
                .thenThrow(new RuntimeException("token revoked"));
        when(outlookTokenRefresher.refreshAccessToken(goodAcc)).thenReturn(tokenResult("ms-token", goodAcc));
        when(calendarAccountRepository.save(goodAcc)).thenReturn(goodAcc);
        when(outlookSyncAdapter.fetchEvents(eq(goodAcc), eq("ms-token"), any(), any()))
                .thenReturn(List.of());

        service.syncAll();

        // Failed account gets markSyncFailed, never updateLastSyncAt
        verify(calendarAccountRepository).markSyncFailed(eq(1L), anyString());
        verify(calendarAccountRepository, never()).updateLastSyncAt(eq(1L), any());
        // Good account still synced and gets a non-null timestamp
        verify(outlookSyncAdapter).fetchEvents(eq(goodAcc), eq("ms-token"), any(), any());
        verify(calendarAccountRepository).updateLastSyncAt(eq(2L), argThat(t -> t != null));
    }

    @Test
    @DisplayName("provider API error skips that account but continues syncing others")
    void providerApiErrorContinues() {
        CalendarAccount acc1 = account(1L, 10L, Provider.GOOGLE);
        CalendarAccount acc2 = account(2L, 20L, Provider.GOOGLE);
        CalendarEvent evt2   = event(2L, 20L, "evt-002");

        when(calendarAccountRepository.findAll()).thenReturn(List.of(acc1, acc2));
        when(googleTokenRefresher.refreshAccessToken(acc1)).thenReturn(tokenResult("tok1", acc1));
        when(googleTokenRefresher.refreshAccessToken(acc2)).thenReturn(tokenResult("tok2", acc2));
        when(calendarAccountRepository.save(acc1)).thenReturn(acc1);
        when(calendarAccountRepository.save(acc2)).thenReturn(acc2);
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
        CalendarAccount acc = account(1L, 10L, Provider.GOOGLE);

        when(calendarAccountRepository.findAll()).thenReturn(List.of(acc));
        when(googleTokenRefresher.refreshAccessToken(acc)).thenReturn(tokenResult("token", acc));
        when(calendarAccountRepository.save(acc)).thenReturn(acc);
        when(googleSyncAdapter.fetchEvents(eq(acc), eq("token"), any(), any()))
                .thenReturn(List.of());

        service.syncAll();

        verify(calendarEventRepository)
                .deleteByCalendarAccountIdAndProviderEventIdNotIn(1L, List.of());
        verify(calendarEventRepository, never()).upsert(any());
    }
}
