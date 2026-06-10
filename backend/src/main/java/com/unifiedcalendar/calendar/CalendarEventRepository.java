package com.unifiedcalendar.calendar;

import java.time.Instant;
import java.util.List;

public interface CalendarEventRepository {
    void upsert(CalendarEvent event);
    void deleteByAccountIdNotIn(Long calendarAccountId, List<String> seenProviderEventIds);
    List<CalendarEvent> findByAdminIdAndTimeRange(Long adminId, Instant from, Instant to);
}
