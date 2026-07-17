package com.unifiedcalendar.booking;

import java.time.Instant;

public record CancellationResponse(
        String message,
        Instant slotStart,
        Instant slotEnd
) {}
