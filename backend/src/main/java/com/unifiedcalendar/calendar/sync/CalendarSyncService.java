package com.unifiedcalendar.calendar.sync;

import com.unifiedcalendar.calendar.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@org.springframework.context.annotation.Profile("!test")
public class CalendarSyncService {

    private static final Logger log = LoggerFactory.getLogger(CalendarSyncService.class);

    private final CalendarAccountRepository calendarAccountRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final GoogleSyncAdapter googleSyncAdapter;
    private final OutlookSyncAdapter outlookSyncAdapter;
    private final GoogleTokenRefresher googleTokenRefresher;
    private final OutlookTokenRefresher outlookTokenRefresher;

    public CalendarSyncService(
            CalendarAccountRepository calendarAccountRepository,
            CalendarEventRepository calendarEventRepository,
            GoogleSyncAdapter googleSyncAdapter,
            OutlookSyncAdapter outlookSyncAdapter,
            GoogleTokenRefresher googleTokenRefresher,
            OutlookTokenRefresher outlookTokenRefresher) {
        this.calendarAccountRepository = calendarAccountRepository;
        this.calendarEventRepository = calendarEventRepository;
        this.googleSyncAdapter = googleSyncAdapter;
        this.outlookSyncAdapter = outlookSyncAdapter;
        this.googleTokenRefresher = googleTokenRefresher;
        this.outlookTokenRefresher = outlookTokenRefresher;
    }

    /** Runs every 5 minutes; each account is isolated so one failure never blocks the others. */
    @Scheduled(fixedDelay = 300_000)
    public void syncAll() {
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
    }

    private void syncAccount(CalendarAccount account) {
        Instant now = Instant.now();
        Instant from = now.minus(1, ChronoUnit.DAYS);
        Instant to   = now.plus(60, ChronoUnit.DAYS);
        String accessToken;
        try {
            accessToken = refreshToken(account);
        } catch (Exception e) {
            log.error("Token refresh failed for account {} — marking last_sync_at null", account.id(), e);
            markSyncFailed(account);
            return;
        }

        List<CalendarEvent> events;
        try {
            events = fetchEvents(account, accessToken, from, to);
        } catch (Exception e) {
            log.error("Provider API error for account {} ({}): {}", account.id(), account.provider(), e.getMessage(), e);
            return;
        }

        List<String> seenIds = events.stream().map(CalendarEvent::providerEventId).toList();
        for (CalendarEvent event : events) {
            calendarEventRepository.upsert(event);
        }
        calendarEventRepository.deleteByCalendarAccountIdAndProviderEventIdNotIn(account.id(), seenIds);

        // Only update last_sync_at — token fields were already persisted by the token refresher and
        // must not be overwritten here (Microsoft may have issued a rotated refresh token).
        calendarAccountRepository.updateLastSyncAt(account.id(), Instant.now());

        log.info("Synced {} event(s) for account {} ({})", events.size(), account.id(), account.provider());
    }

    private String refreshToken(CalendarAccount account) {
        return switch (account.provider()) {
            case "GOOGLE"  -> googleTokenRefresher.refreshAccessToken(account);
            case "OUTLOOK" -> outlookTokenRefresher.refreshAccessToken(account);
            default -> throw new IllegalArgumentException("Unknown provider: " + account.provider());
        };
    }

    private List<CalendarEvent> fetchEvents(CalendarAccount account, String accessToken, Instant from, Instant to) {
        return switch (account.provider()) {
            case "GOOGLE"  -> googleSyncAdapter.fetchEvents(account, accessToken, from, to);
            case "OUTLOOK" -> outlookSyncAdapter.fetchEvents(account, accessToken, from, to);
            default -> throw new IllegalArgumentException("Unknown provider: " + account.provider());
        };
    }

    /** Sets last_sync_at to null to signal that this account has not successfully synced. */
    private void markSyncFailed(CalendarAccount account) {
        try {
            calendarAccountRepository.updateLastSyncAt(account.id(), null);
        } catch (Exception ex) {
            log.warn("Could not mark account {} as sync-failed: {}", account.id(), ex.getMessage());
        }
    }
}
