package com.unifiedcalendar.calendar;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CalendarEventRepository {
    /** Inserts or updates a normalized provider event in the local cache. */
    void upsert(CalendarEvent event);

    /** Deletes cached events for an account whose providerEventId was not seen in the latest sync. */
    void deleteByCalendarAccountIdAndProviderEventIdNotIn(Long calendarAccountId, List<String> seenProviderEventIds);

    /** Returns cached events within a UTC time range for availability computation. */
    List<CalendarEvent> findByAdminIdAndTimeRange(Long adminId, Instant from, Instant to);

    /** Finds a normalized event by its database id. */
    Optional<CalendarEvent> findById(Long id);

    /** Returns events with their account email for the unified calendar view; uses an overlap query. */
    List<CalendarEventResponse> findWithEmailByAdminIdAndTimeRange(Long adminId, Instant start, Instant end);

    /** Inserts a booking-created calendar event with is_booking_event=true and returns its generated id. */
    Long insertBookingEvent(CalendarEvent event);

    /** Loads one cached event by id, constrained to its owning admin. */
    Optional<CalendarEvent> findById(Long id, Long adminId);

    /** Deletes one cached event by id, constrained to its owning admin. */
    void deleteById(Long id, Long adminId);

    /** Updates only the cached time range of one event, constrained to its owning admin. */
    void updateTime(Long id, Long adminId, Instant start, Instant end);
}
