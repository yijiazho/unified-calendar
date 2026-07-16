package com.unifiedcalendar.calendar;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;

@Service
public class GoogleProviderEventService implements ProviderEventService {

    private static final Logger log = LoggerFactory.getLogger(GoogleProviderEventService.class);

    private final HttpTransport httpTransport;

    public GoogleProviderEventService(HttpTransport googleHttpTransport) {
        this.httpTransport = googleHttpTransport;
    }

    @Override
    public boolean supports(Provider provider) {
        return provider == Provider.GOOGLE;
    }

    /** Queries Google Calendar for any events overlapping the slot; a non-empty result means a conflict exists. */
    @Override
    public boolean hasConflict(CalendarAccount account, String accessToken, Instant start, Instant end) {
        Calendar service = buildService(accessToken);
        try {
            Events events = service.events().list("primary")
                    .setTimeMin(new DateTime(start.toEpochMilli()))
                    .setTimeMax(new DateTime(end.toEpochMilli()))
                    .setSingleEvents(true)
                    .execute();
            List<Event> items = events.getItems();
            return items != null && !items.isEmpty();
        } catch (Exception e) {
            throw new RuntimeException("Google Calendar conflict check failed for account " + account.id(), e);
        }
    }

    /** Inserts an event into the primary Google Calendar and returns its provider-assigned ID. */
    @Override
    public String createEvent(CalendarAccount account, String accessToken,
                              String title, String description, Instant start, Instant end) {
        Calendar service = buildService(accessToken);
        Event event = new Event()
                .setSummary(title)
                .setDescription(description);
        EventDateTime startDt = new EventDateTime()
                .setDateTime(new DateTime(start.toEpochMilli()))
                .setTimeZone("UTC");
        EventDateTime endDt = new EventDateTime()
                .setDateTime(new DateTime(end.toEpochMilli()))
                .setTimeZone("UTC");
        event.setStart(startDt).setEnd(endDt);
        try {
            Event created = service.events().insert("primary", event).execute();
            log.info("Created Google Calendar event {} for account {}", created.getId(), account.id());
            return created.getId();
        } catch (Exception e) {
            throw new RuntimeException("Google Calendar event creation failed for account " + account.id(), e);
        }
    }

    /** Deletes a Google Calendar event by its provider event ID; used for booking rollback. */
    @Override
    public void deleteEvent(CalendarAccount account, String accessToken, String providerEventId) {
        Calendar service = buildService(accessToken);
        try {
            service.events().delete("primary", providerEventId).execute();
            log.info("Deleted Google Calendar event {} for account {}", providerEventId, account.id());
        } catch (Exception e) {
            throw new RuntimeException("Google Calendar event deletion failed for account " + account.id(), e);
        }
    }

    private Calendar buildService(String accessToken) {
        return new Calendar.Builder(
                httpTransport,
                GsonFactory.getDefaultInstance(),
                request -> request.getHeaders().setAuthorization("Bearer " + accessToken))
                .setApplicationName("unified-calendar")
                .build();
    }
}
