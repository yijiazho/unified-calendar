package com.unifiedcalendar.booking;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record RescheduleRequest(
        @NotNull(message = "must not be null") Instant newSlotStart,
        @NotNull(message = "must not be null") Instant newSlotEnd
) {}
