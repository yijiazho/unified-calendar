package com.unifiedcalendar.calendar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
public class OutlookProviderEventService implements ProviderEventService {

    private static final Logger log = LoggerFactory.getLogger(OutlookProviderEventService.class);
    private static final String GRAPH_CALENDAR_VIEW = "https://graph.microsoft.com/v1.0/me/calendarView";
    private static final String GRAPH_EVENTS = "https://graph.microsoft.com/v1.0/me/events";

    private final RestClient restClient;

    public OutlookProviderEventService(@Qualifier("microsoftRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public boolean supports(Provider provider) {
        return provider == Provider.OUTLOOK;
    }

    /** Queries Microsoft Graph calendarView for any events overlapping the slot. */
    @Override
    public boolean hasConflict(CalendarAccount account, String accessToken, Instant start, Instant end) {
        String url = GRAPH_CALENDAR_VIEW
                + "?startDateTime=" + start.truncatedTo(ChronoUnit.SECONDS)
                + "&endDateTime=" + end.truncatedTo(ChronoUnit.SECONDS);
        try {
            Map<String, Object> response = restClient.get()
                    .uri(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Prefer", "outlook.timezone=\"UTC\"")
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (response == null) return false;
            @SuppressWarnings("unchecked")
            List<Object> items = (List<Object>) response.get("value");
            return items != null && !items.isEmpty();
        } catch (Exception e) {
            throw new RuntimeException("Outlook Calendar conflict check failed for account " + account.id(), e);
        }
    }

    /** Creates an event via Microsoft Graph and returns its provider-assigned ID. */
    @Override
    public String createEvent(CalendarAccount account, String accessToken,
                              String title, String description, Instant start, Instant end) {
        Map<String, Object> body = Map.of(
                "subject", title,
                "body", Map.of("contentType", "Text", "content", description),
                "start", Map.of("dateTime", start.truncatedTo(ChronoUnit.SECONDS).toString().replace("Z", ""), "timeZone", "UTC"),
                "end",   Map.of("dateTime", end.truncatedTo(ChronoUnit.SECONDS).toString().replace("Z", ""), "timeZone", "UTC")
        );
        try {
            Map<String, Object> created = restClient.post()
                    .uri(GRAPH_EVENTS)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (created == null || created.get("id") == null) {
                throw new RuntimeException("Microsoft Graph returned no event ID");
            }
            String eventId = (String) created.get("id");
            log.info("Created Outlook Calendar event {} for account {}", eventId, account.id());
            return eventId;
        } catch (Exception e) {
            throw new RuntimeException("Outlook Calendar event creation failed for account " + account.id(), e);
        }
    }

    /** Patches only the start and end of an existing Microsoft Graph event. */
    @Override
    public void updateEvent(CalendarAccount account, String accessToken, String providerEventId,
                            Instant start, Instant end) {
        Map<String, Object> body = Map.of(
                "start", graphDateTime(start),
                "end", graphDateTime(end)
        );
        try {
            restClient.patch()
                    .uri(GRAPH_EVENTS + "/{id}", providerEventId)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Updated Outlook Calendar event {} for account {}", providerEventId, account.id());
        } catch (Exception e) {
            throw new RuntimeException("Outlook Calendar event update failed for account " + account.id(), e);
        }
    }

    /** Deletes an Outlook Calendar event by its provider event ID; used for booking rollback. */
    @Override
    public void deleteEvent(CalendarAccount account, String accessToken, String providerEventId) {
        try {
            restClient.delete()
                    .uri(GRAPH_EVENTS + "/{id}", providerEventId)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Deleted Outlook Calendar event {} for account {}", providerEventId, account.id());
        } catch (Exception e) {
            throw new RuntimeException("Outlook Calendar event deletion failed for account " + account.id(), e);
        }
    }

    private Map<String, String> graphDateTime(Instant instant) {
        return Map.of(
                "dateTime", instant.truncatedTo(ChronoUnit.SECONDS).toString().replace("Z", ""),
                "timeZone", "UTC");
    }
}
