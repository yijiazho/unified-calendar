package com.unifiedcalendar.availability;

import java.util.List;

/** Response body for GET /availability. */
public record AvailabilityResponse(String date, String adminTimezone, List<TimeSlotResponse> slots) {}
