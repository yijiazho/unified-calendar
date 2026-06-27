package com.unifiedcalendar.calendar.sync;

/** Triggers a full sync of all connected calendar accounts. */
public interface CalendarSyncer {
    void syncAll();
}
