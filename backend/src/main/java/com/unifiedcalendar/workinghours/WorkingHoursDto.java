package com.unifiedcalendar.workinghours;

public record WorkingHoursDto(
        Integer dayOfWeek,
        String startTime,
        String endTime
) {}
