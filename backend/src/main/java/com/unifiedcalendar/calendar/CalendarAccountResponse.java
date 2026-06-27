package com.unifiedcalendar.calendar;

import java.time.Instant;

public record CalendarAccountResponse(
        Long id,
        String provider,
        String email,
        boolean isPrimary,
        Instant connectedAt,
        Instant lastSyncAt,
        String lastSyncError
) {
    /** Maps a domain CalendarAccount to the response shape (omitting encrypted tokens). */
    public static CalendarAccountResponse from(CalendarAccount account) {
        return new CalendarAccountResponse(
                account.id(), account.provider().name(), account.email(),
                account.isPrimary(), account.connectedAt(),
                account.lastSyncAt(), account.lastSyncError());
    }
}
