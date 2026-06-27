package com.unifiedcalendar.calendar;

import java.time.Instant;

public record CalendarEvent(
        Long id,
        Long adminId,
        Long calendarAccountId,
        Provider provider,
        String providerEventId,
        String title,
        Instant startTimeUtc,
        Instant endTimeUtc,
        boolean isBookingEvent,
        Instant providerUpdatedAt,
        Instant lastSyncedAt
) {}
