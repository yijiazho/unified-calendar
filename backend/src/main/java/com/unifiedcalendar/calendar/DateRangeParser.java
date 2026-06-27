package com.unifiedcalendar.calendar;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/** Parses date or datetime strings into UTC Instants for calendar event queries. */
public final class DateRangeParser {

    private DateRangeParser() {}

    /** Parses a date-only ("2024-03-01") or full datetime string to a start Instant (date-only → midnight UTC). */
    public static Instant parseStart(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {}
        try {
            return LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid 'start' parameter: expected ISO-8601 date or datetime");
        }
    }

    /** Parses a date-only ("2024-03-01") or full datetime string to an end Instant (date-only → 23:59:59 UTC). */
    public static Instant parseEnd(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {}
        try {
            return LocalDate.parse(value).atTime(23, 59, 59).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid 'end' parameter: expected ISO-8601 date or datetime");
        }
    }
}
