package com.unifiedcalendar.calendar;

import java.time.Instant;
import java.util.List;

public interface CalendarEventRepository {
    /** Inserts or updates a normalized provider event in the local cache. */
    void upsert(CalendarEvent event);

    /** Deletes cached events for an account whose providerEventId was not seen in the latest sync. */
    void deleteByCalendarAccountIdAndProviderEventIdNotIn(Long calendarAccountId, List<String> seenProviderEventIds);

    /** Returns cached events within a UTC time range for availability computation. */
    List<CalendarEvent> findByAdminIdAndTimeRange(Long adminId, Instant from, Instant to);

    /** Returns events with their account email for the unified calendar view; uses an overlap query. */
    List<CalendarEventResponse> findWithEmailByAdminIdAndTimeRange(Long adminId, Instant start, Instant end);
}
