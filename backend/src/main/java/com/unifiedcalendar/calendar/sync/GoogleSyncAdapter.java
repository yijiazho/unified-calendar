package com.unifiedcalendar.calendar.sync;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import com.unifiedcalendar.calendar.CalendarAccount;
import com.unifiedcalendar.calendar.CalendarEvent;
import com.unifiedcalendar.calendar.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class GoogleSyncAdapter implements SyncAdapter {

    private static final Logger log = LoggerFactory.getLogger(GoogleSyncAdapter.class);

    private final HttpTransport httpTransport;

    public GoogleSyncAdapter(HttpTransport googleHttpTransport) {
        this.httpTransport = googleHttpTransport;
    }

    @Override
    public boolean supports(Provider provider) {
        return provider == Provider.GOOGLE;
    }

    /** Fetches all timed events (including recurring instances) from the primary Google Calendar for the given window. */
    @Override
    public List<CalendarEvent> fetchEvents(CalendarAccount account, String accessToken, Instant from, Instant to) {
        Calendar calendarService = new Calendar.Builder(
                httpTransport,
                GsonFactory.getDefaultInstance(),
                request -> request.getHeaders().setAuthorization("Bearer " + accessToken))
                .setApplicationName("unified-calendar")
                .build();

        List<CalendarEvent> result = new ArrayList<>();
        int rawCount = 0;
        String pageToken = null;
        try {
            do {
                Events events = calendarService.events().list("primary")
                        .setTimeMin(new DateTime(from.toEpochMilli()))
                        .setTimeMax(new DateTime(to.toEpochMilli()))
                        .setSingleEvents(true)
                        .setPageToken(pageToken)
                        .execute();

                List<Event> items = events.getItems();
                if (items != null) {
                    for (Event event : items) {
                        rawCount++;
                        CalendarEvent normalized = normalize(event, account);
                        if (normalized != null) {
                            result.add(normalized);
                        }
                    }
                }
                pageToken = events.getNextPageToken();
            } while (pageToken != null);
        } catch (Exception e) {
            throw new RuntimeException("Google Calendar API error for account " + account.id(), e);
        }
        log.info("Google Calendar fetched {} raw event(s), kept {} for account {} (all-day events skipped)",
                rawCount, result.size(), account.id());
        return result;
    }

    /** Returns null for all-day events; recurring instances are included since setSingleEvents(true) gives them stable IDs. */
    private CalendarEvent normalize(Event event, CalendarAccount account) {
        // All-day events carry a date field instead of dateTime; skip them per MVP scope
        if (event.getStart() == null || event.getStart().getDateTime() == null) {
            log.debug("Skipping all-day Google event {} for account {}", event.getId(), account.id());
            return null;
        }
        if (event.getEnd() == null || event.getEnd().getDateTime() == null) {
            return null;
        }

        Instant startTimeUtc = Instant.ofEpochMilli(event.getStart().getDateTime().getValue());
        Instant endTimeUtc   = Instant.ofEpochMilli(event.getEnd().getDateTime().getValue());
        Instant updatedAt    = event.getUpdated() != null
                ? Instant.ofEpochMilli(event.getUpdated().getValue())
                : null;

        return new CalendarEvent(
                null,
                account.adminId(),
                account.id(),
                Provider.GOOGLE,
                event.getId(),
                event.getSummary(),
                startTimeUtc,
                endTimeUtc,
                false,
                updatedAt,
                null
        );
    }
}
