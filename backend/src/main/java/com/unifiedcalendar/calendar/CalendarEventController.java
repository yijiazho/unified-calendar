package com.unifiedcalendar.calendar;

import com.unifiedcalendar.auth.SessionUtils;
import com.unifiedcalendar.calendar.sync.CalendarSyncer;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/calendar")
public class CalendarEventController {

    private static final Logger log = LoggerFactory.getLogger(CalendarEventController.class);

    private final CalendarEventRepository eventRepository;
    private final CalendarSyncer syncer;

    public CalendarEventController(CalendarEventRepository eventRepository, CalendarSyncer syncer) {
        this.eventRepository = eventRepository;
        this.syncer = syncer;
    }

    /**
     * Returns all synced events for the authenticated admin that overlap the given UTC date/time range.
     * Accepts both date-only ("2024-03-01") and full datetime strings; date-only start defaults to
     * midnight UTC, date-only end defaults to 23:59:59 UTC.
     */
    @GetMapping("/events")
    public List<CalendarEventResponse> listEvents(
            @RequestParam String start,
            @RequestParam String end,
            HttpSession session) {
        Long adminId = SessionUtils.requireAdminId(session);
        Instant from = DateRangeParser.parseStart(start);
        Instant to   = DateRangeParser.parseEnd(end);
        return eventRepository.findWithEmailByAdminIdAndTimeRange(adminId, from, to);
    }

    /** Triggers an immediate background sync for all connected calendar accounts; returns 202 immediately. */
    @PostMapping("/sync")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void triggerSync(HttpSession session) {
        SessionUtils.requireAdminId(session);
        CompletableFuture.runAsync(syncer::syncAll);
    }
}
