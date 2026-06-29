package com.unifiedcalendar.availability;

import java.time.Instant;

/** A bookable 30-minute slot returned by the availability engine. */
public record TimeSlot(Instant start, Instant end) {}
