package com.unifiedcalendar.workinghours;

public record WorkingHours(
        Long id,
        Long adminId,
        int dayOfWeek,
        String startTime,
        String endTime
) {}
