package com.unifiedcalendar.availability;

import java.time.Instant;

/** An immutable half-open time interval [start, end) used for interval arithmetic. */
public record Interval(Instant start, Instant end) {}
