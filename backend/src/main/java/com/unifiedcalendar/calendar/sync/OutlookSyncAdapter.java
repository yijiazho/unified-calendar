package com.unifiedcalendar.calendar.sync;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unifiedcalendar.calendar.CalendarAccount;
import com.unifiedcalendar.calendar.CalendarEvent;
import com.unifiedcalendar.calendar.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
public class OutlookSyncAdapter implements SyncAdapter {

    private static final Logger log = LoggerFactory.getLogger(OutlookSyncAdapter.class);
    private static final String GRAPH_CALENDAR_VIEW = "https://graph.microsoft.com/v1.0/me/calendarView";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OutlookSyncAdapter(
            @Qualifier("microsoftRestClient") RestClient restClient,
            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(Provider provider) {
        return provider == Provider.OUTLOOK;
    }

    /** Fetches all non-recurring events from Microsoft Graph calendarView for the given window. */
    @Override
    public List<CalendarEvent> fetchEvents(CalendarAccount account, String accessToken, Instant from, Instant to) {
        String initialUrl = GRAPH_CALENDAR_VIEW
                + "?startDateTime=" + from.truncatedTo(ChronoUnit.SECONDS)
                + "&endDateTime=" + to.truncatedTo(ChronoUnit.SECONDS);

        logTokenClaims(account.id(), accessToken);

        List<CalendarEvent> result = new ArrayList<>();
        int rawCount = 0;
        String url = initialUrl;
        try {
            while (url != null) {
                Map<String, Object> response = restClient.get()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Prefer", "outlook.timezone=\"UTC\"")
                        .retrieve()
                        .onStatus(status -> status.isError(), (req, resp) -> {
                            byte[] body = resp.getBody().readAllBytes();
                            String bodyText = body.length > 0 ? new String(body, StandardCharsets.UTF_8) : "[no body]";
                            if (resp.getStatusCode().value() == 401 && body.length == 0) {
                                // Empty-body 401 on calendarView is Microsoft Graph's signal that the account
                                // has no Exchange Online mailbox. A token with a valid aud/scp still fails here.
                                log.error("Microsoft Graph 401 (no body) for account {} — account likely has no " +
                                        "Exchange Online mailbox. Connect a personal Outlook.com account " +
                                        "(set MICROSOFT_TENANT_ID=common) or use an M365-licensed work account.",
                                        account.id());
                            } else {
                                log.error("Microsoft Graph {} for account {}: {}", resp.getStatusCode(), account.id(), bodyText);
                            }
                            throw new RuntimeException("Microsoft Graph " + resp.getStatusCode() + " for account " + account.id() + ": " + bodyText);
                        })
                        .body(new ParameterizedTypeReference<Map<String, Object>>() {});

                if (response == null) break;

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> events = (List<Map<String, Object>>) response.get("value");
                if (events != null) {
                    rawCount += events.size();
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
        log.info("Microsoft Graph fetched {} raw event(s), kept {} for account {} (all-day events skipped)",
                rawCount, result.size(), account.id());
        return result;
    }

    /**
     * Decodes the JWT payload and logs aud/scp/tid claims to help diagnose 401 errors from Graph.
     * Uses ObjectMapper for robust JSON parsing instead of regex on the raw payload string.
     */
    private void logTokenClaims(Long accountId, String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) {
                log.error("Token for account {} is not a JWT (got {} segments, expected 3)", accountId, parts.length);
                return;
            }
            String raw = parts[1];
            int pad = (4 - raw.length() % 4) % 4;
            byte[] bytes = Base64.getUrlDecoder().decode(raw + "=".repeat(pad));
            Map<String, Object> claims = objectMapper.readValue(
                    new String(bytes, StandardCharsets.UTF_8),
                    new TypeReference<Map<String, Object>>() {});
            log.debug("Token claims for account {}: aud=[{}] scp=[{}] tid=[{}] roles=[{}]",
                    accountId,
                    claims.get("aud"), claims.get("scp"), claims.get("tid"), claims.get("roles"));
        } catch (Exception e) {
            log.error("Could not decode token for account {}: {}", accountId, e.getMessage());
        }
    }

    /** Returns null for all-day events; recurring occurrences/exceptions are included because calendarView gives them stable IDs. */
    private CalendarEvent normalize(Map<String, Object> event, CalendarAccount account) {
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
                Provider.OUTLOOK,
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
