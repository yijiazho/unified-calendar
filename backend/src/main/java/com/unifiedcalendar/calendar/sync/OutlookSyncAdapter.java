package com.unifiedcalendar.calendar.sync;

import com.unifiedcalendar.calendar.CalendarAccount;
import com.unifiedcalendar.calendar.CalendarEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class OutlookSyncAdapter {

    private static final Logger log = LoggerFactory.getLogger(OutlookSyncAdapter.class);
    private static final String GRAPH_CALENDAR_VIEW = "https://graph.microsoft.com/v1.0/me/calendarView";

    private final RestClient restClient;

    public OutlookSyncAdapter(@Qualifier("microsoftRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /** Fetches all non-recurring events from Microsoft Graph calendarView for the given window. */
    public List<CalendarEvent> fetchEvents(CalendarAccount account, String accessToken, Instant from, Instant to) {
        String initialUrl = GRAPH_CALENDAR_VIEW
                + "?startDateTime=" + from.truncatedTo(ChronoUnit.SECONDS)
                + "&endDateTime=" + to.truncatedTo(ChronoUnit.SECONDS);

        List<CalendarEvent> result = new ArrayList<>();
        String url = initialUrl;
        try {
            while (url != null) {
                Map<String, Object> response = restClient.get()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Prefer", "outlook.timezone=\"UTC\"")
                        .retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() {});

                if (response == null) break;

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> events = (List<Map<String, Object>>) response.get("value");
                if (events != null) {
                    for (Map<String, Object> event : events) {
                        CalendarEvent normalized = normalize(event, account);
                        if (normalized != null) {
                            result.add(normalized);
                        }
                    }
                }
                url = (String) response.get("@odata.nextLink");
            }
        } catch (Exception e) {
            throw new RuntimeException("Microsoft Graph API error for account " + account.id(), e);
        }
        return result;
    }

    /** Returns null for recurring occurrences/exceptions; they have no reliable single-event identity. */
    private CalendarEvent normalize(Map<String, Object> event, CalendarAccount account) {
        String type = (String) event.get("type");
        if ("occurrence".equals(type) || "exception".equals(type)) {
            return null;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> start = (Map<String, Object>) event.get("start");
        @SuppressWarnings("unchecked")
        Map<String, Object> end = (Map<String, Object>) event.get("end");

        if (start == null || end == null) {
            log.debug("Skipping Outlook event {} with missing start/end for account {}", event.get("id"), account.id());
            return null;
        }

        String startStr = (String) start.get("dateTime");
        String endStr   = (String) end.get("dateTime");
        if (startStr == null || endStr == null) {
            log.debug("Skipping Outlook all-day event {} for account {}", event.get("id"), account.id());
            return null;
        }

        // Graph returns UTC local-datetime strings (no Z suffix) when Prefer: outlook.timezone="UTC" is sent
        Instant startTimeUtc = LocalDateTime.parse(startStr).toInstant(ZoneOffset.UTC);
        Instant endTimeUtc   = LocalDateTime.parse(endStr).toInstant(ZoneOffset.UTC);

        String lastModified = (String) event.get("lastModifiedDateTime");
        Instant updatedAt   = lastModified != null ? Instant.parse(lastModified) : null;

        return new CalendarEvent(
                null,
                account.adminId(),
                account.id(),
                "OUTLOOK",
                (String) event.get("id"),
                (String) event.get("subject"),
                startTimeUtc,
                endTimeUtc,
                false,
                updatedAt,
                null
        );
    }
}
