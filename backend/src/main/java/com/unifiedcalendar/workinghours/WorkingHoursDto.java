package com.unifiedcalendar.workinghours;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record WorkingHoursDto(
        @NotNull(message = "is required") @Min(0) @Max(6) Integer dayOfWeek,
        @NotNull @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "must be HH:MM (00:00–23:59)")
        String startTime,
        @NotNull @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "must be HH:MM (00:00–23:59)")
        String endTime
) {}
