package com.unifiedcalendar.calendar;

public record CalendarEventResponse(
        Long id,
        String title,
        String start,
        String end,
        String provider,
        Long calendarAccountId,
        String calendarEmail,
        boolean isBookingEvent
) {}
