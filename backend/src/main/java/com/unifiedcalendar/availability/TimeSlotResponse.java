package com.unifiedcalendar.availability;

/** JSON-serialisable slot sent to the public availability page. */
public record TimeSlotResponse(String start, String end) {}
