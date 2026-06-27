package com.unifiedcalendar.calendar.sync;

import com.unifiedcalendar.calendar.CalendarAccount;
import com.unifiedcalendar.calendar.CalendarEvent;
import com.unifiedcalendar.calendar.Provider;

import java.time.Instant;
import java.util.List;

/** Fetches events from one calendar provider — implement to add a new provider with zero changes to CalendarSyncService. */
public interface SyncAdapter {
    /** Returns true if this adapter handles the given provider. */
    boolean supports(Provider provider);

    /** Fetches all timed events from the provider that overlap the given UTC window. */
    List<CalendarEvent> fetchEvents(CalendarAccount account, String accessToken, Instant from, Instant to);
}
