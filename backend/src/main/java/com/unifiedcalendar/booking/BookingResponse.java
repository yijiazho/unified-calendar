package com.unifiedcalendar.booking;

import java.time.Instant;

public record BookingResponse(
        Long bookingId,
        String visitorName,
        Instant slotStart,
        Instant slotEnd,
        String adminName,
        String cancelToken,
        String rescheduleToken
) {}
