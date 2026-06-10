package com.unifiedcalendar.booking;

import java.time.Instant;

public record Booking(
        Long id,
        Long adminId,
        Long calendarEventId,
        String visitorName,
        String visitorEmail,
        String visitorPhone,
        String notes,
        String status,
        String cancelToken,
        String rescheduleToken,
        Instant createdAt
) {}
