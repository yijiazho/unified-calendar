package com.unifiedcalendar.calendar.sync;

import com.unifiedcalendar.calendar.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@org.springframework.context.annotation.Profile("!test")
public class CalendarSyncService implements CalendarSyncer {

    private static final Logger log = LoggerFactory.getLogger(CalendarSyncService.class);

    private final AtomicBoolean running = new AtomicBoolean(false);

    private final CalendarAccountRepository calendarAccountRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final GoogleTokenRefresher googleTokenRefresher;
    private final OutlookTokenRefresher outlookTokenRefresher;
    private final List<SyncAdapter> syncAdapters;

    public CalendarSyncService(
            CalendarAccountRepository calendarAccountRepository,
            CalendarEventRepository calendarEventRepository,
            GoogleTokenRefresher googleTokenRefresher,
            OutlookTokenRefresher outlookTokenRefresher,
            List<SyncAdapter> syncAdapters) {
        this.calendarAccountRepository = calendarAccountRepository;
        this.calendarEventRepository = calendarEventRepository;
        this.googleTokenRefresher = googleTokenRefresher;
        this.outlookTokenRefresher = outlookTokenRefresher;
        this.syncAdapters = syncAdapters;
    }

    /** Runs every 5 minutes; each account is isolated so one failure never blocks the others. */
    @Scheduled(fixedDelay = 300_000)
    @Override
    public void syncAll() {
        if (!running.compareAndSet(false, true)) {
            log.info("Sync already in progress, skipping trigger");
            return;
        }
        try {
            List<CalendarAccount> accounts = calendarAccountRepository.findAll();
            log.info("Sync cycle starting for {} account(s)", accounts.size());
            for (CalendarAccount account : accounts) {
                try {
                    syncAccount(account);
                } catch (Exception e) {
                    log.error("Sync failed for account {} ({}): {}", account.id(), account.provider(), e.getMessage(), e);
                }
            }
            log.info("Sync cycle complete");
        } finally {
            running.set(false);
        }
    }

    private void syncAccount(CalendarAccount account) {
        Instant now = Instant.now();
        Instant from = now.minus(1, ChronoUnit.DAYS);
        Instant to   = now.plus(60, ChronoUnit.DAYS);

        TokenRefreshResult refreshResult;
        try {
            refreshResult = refreshToken(account);
        } catch (Exception e) {
            log.error("Token refresh failed for account {} — recording error", account.id(), e);
            markSyncFailed(account, e.getMessage());
            return;
        }
        // Persist the updated tokens (e.g. rotated Microsoft refresh token) before any API call.
        calendarAccountRepository.save(refreshResult.updatedAccount());

        List<CalendarEvent> events;
        try {
            events = fetchEvents(account, refreshResult.accessToken(), from, to);
        } catch (Exception e) {
            log.error("Provider API error for account {} ({}): {}", account.id(), account.provider(), e.getMessage(), e);
            return;
        }

        List<String> seenIds = events.stream().map(CalendarEvent::providerEventId).toList();
        for (CalendarEvent event : events) {
            calendarEventRepository.upsert(event);
        }
        calendarEventRepository.deleteByCalendarAccountIdAndProviderEventIdNotIn(account.id(), seenIds);

        // Record successful sync — also clears last_sync_error.
        calendarAccountRepository.updateLastSyncAt(account.id(), Instant.now());

        log.info("Synced {} event(s) for account {} ({})", events.size(), account.id(), account.provider());
    }

    private TokenRefreshResult refreshToken(CalendarAccount account) {
        return switch (account.provider()) {
            case GOOGLE  -> googleTokenRefresher.refreshAccessToken(account);
            case OUTLOOK -> outlookTokenRefresher.refreshAccessToken(account);
        };
    }

    private List<CalendarEvent> fetchEvents(CalendarAccount account, String accessToken, Instant from, Instant to) {
        return syncAdapters.stream()
                .filter(a -> a.supports(account.provider()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No sync adapter for provider: " + account.provider()))
                .fetchEvents(account, accessToken, from, to);
    }

    private void markSyncFailed(CalendarAccount account, String error) {
        try {
            calendarAccountRepository.markSyncFailed(account.id(), error);
        } catch (Exception ex) {
            log.warn("Could not mark account {} as sync-failed: {}", account.id(), ex.getMessage());
        }
    }
}
