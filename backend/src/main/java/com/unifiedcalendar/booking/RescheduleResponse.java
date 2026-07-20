package com.unifiedcalendar.booking;

import java.time.Instant;

public record RescheduleResponse(
        Long bookingId,
        String visitorName,
        Instant newSlotStart,
        Instant newSlotEnd,
        String cancelToken,
        String rescheduleToken
) {}
