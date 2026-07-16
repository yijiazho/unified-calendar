package com.unifiedcalendar.booking;

import java.time.Instant;
import java.util.Optional;

public interface SlotReservationRepository {
    /** Atomically reserves a slot; throws DataIntegrityViolationException if slot already reserved. */
    SlotReservation reserve(Long adminId, Instant slotStart, Instant slotEnd);

    /** Finds an existing reservation by slot boundaries. */
    Optional<SlotReservation> findBySlot(Long adminId, Instant slotStart, Instant slotEnd);

    /** Deletes a reservation, freeing the slot for other requests. */
    void delete(Long reservationId);
}
