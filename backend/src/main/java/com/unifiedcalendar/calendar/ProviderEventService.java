package com.unifiedcalendar.calendar;

import java.time.Instant;

/** Abstracts provider-specific event creation, conflict checking, and deletion for the booking flow. */
public interface ProviderEventService {

    /** Returns true if this service handles the given provider. */
    boolean supports(Provider provider);

    /**
     * Returns true if any event in the provider calendar overlaps [start, end).
     * Used as the live double-check before confirming a booking.
     */
    boolean hasConflict(CalendarAccount account, String accessToken, Instant start, Instant end);

    /**
     * Creates a calendar event and returns the provider-assigned event ID.
     * Called during booking creation; the returned ID is stored in calendar_events.
     */
    String createEvent(CalendarAccount account, String accessToken,
                       String title, String description, Instant start, Instant end);

    /**
     * Deletes a provider calendar event by its ID.
     * Used for rollback when the database insert fails after provider event creation.
     */
    void deleteEvent(CalendarAccount account, String accessToken, String providerEventId);
}
