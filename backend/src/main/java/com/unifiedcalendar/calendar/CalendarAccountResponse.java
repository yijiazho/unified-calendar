package com.unifiedcalendar.calendar;

import java.time.Instant;

public record CalendarAccountResponse(
        Long id,
        String provider,
        String email,
        boolean isPrimary,
        Instant connectedAt
) {
    /** Maps a domain CalendarAccount to the response shape (omitting encrypted tokens). */
    public static CalendarAccountResponse from(CalendarAccount account) {
        return new CalendarAccountResponse(
                account.id(), account.provider(), account.email(),
                account.isPrimary(), account.connectedAt());
    }
}
