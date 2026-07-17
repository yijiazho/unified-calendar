package com.unifiedcalendar.booking;

import java.util.Optional;

public interface BookingRepository {
    /** Persists a booking and returns the stored record (including any generated id). */
    Booking save(Booking booking);

    /** Finds a booking by its visitor-facing cancel token. */
    Optional<Booking> findByCancelToken(String token);

    /** Finds a booking by its visitor-facing reschedule token. */
    Optional<Booking> findByRescheduleToken(String token);

    /** Updates a booking status without modifying other fields. */
    void updateStatus(Long id, String status);

    /**
     * Changes the status only when it still has the expected value.
     * Returns true when the row was updated, allowing callers to reject concurrent duplicate actions.
     */
    boolean updateStatusIfCurrent(Long id, String expectedStatus, String newStatus);
}
