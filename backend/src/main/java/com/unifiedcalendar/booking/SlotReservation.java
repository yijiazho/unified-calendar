package com.unifiedcalendar.booking;

import java.time.Instant;

/** Represents an atomic slot reservation. Backed by unique constraint to prevent concurrent bookings. */
public record SlotReservation(
        Long id,
        Long adminId,
        Instant slotStart,
        Instant slotEnd,
        Instant reservedAt
) {}
