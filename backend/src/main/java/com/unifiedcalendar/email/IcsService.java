package com.unifiedcalendar.email;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Service
public class IcsService {

    private static final DateTimeFormatter ICS_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final Clock clock;

    public IcsService() {
        this(Clock.systemUTC());
    }

    IcsService(Clock clock) {
        this.clock = clock;
    }

    public byte[] generate(String uid, String summary, String description,
                           Instant start, Instant end, String organizerEmail) {
        StringBuilder ics = new StringBuilder();
        append(ics, "BEGIN:VCALENDAR");
        append(ics, "VERSION:2.0");
        append(ics, "PRODID:-//Unified Calendar//EN");
        append(ics, "CALSCALE:GREGORIAN");
        append(ics, "METHOD:REQUEST");
        append(ics, "BEGIN:VEVENT");
        append(ics, "UID:" + escape(uid) + "@unified-calendar");
        append(ics, "DTSTAMP:" + ICS_FORMATTER.format(clock.instant()));
        append(ics, "DTSTART:" + ICS_FORMATTER.format(start));
        append(ics, "DTEND:" + ICS_FORMATTER.format(end));
        append(ics, "SUMMARY:" + escape(summary));
        append(ics, "DESCRIPTION:" + escape(description));
        append(ics, "ORGANIZER:mailto:" + escape(organizerEmail));
        append(ics, "STATUS:CONFIRMED");
        append(ics, "END:VEVENT");
        append(ics, "END:VCALENDAR");
        return ics.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void append(StringBuilder target, String line) {
        target.append(fold(line));
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n")
                .replace(",", "\\,")
                .replace(";", "\\;");
    }

    /** Folds by UTF-8 octets while keeping continuation lines within RFC 5545's 75-octet limit. */
    private String fold(String line) {
        StringBuilder folded = new StringBuilder();
        int index = 0;
        boolean continuation = false;
        while (index < line.length()) {
            int byteLimit = continuation ? 74 : 75;
            int end = index;
            int bytes = 0;
            while (end < line.length()) {
                int codePoint = line.codePointAt(end);
                int codePointBytes = new String(Character.toChars(codePoint))
                        .getBytes(StandardCharsets.UTF_8).length;
                if (bytes + codePointBytes > byteLimit) {
                    break;
                }
                bytes += codePointBytes;
                end += Character.charCount(codePoint);
            }
            if (continuation) {
                folded.append(' ');
            }
            folded.append(line, index, end).append("\r\n");
            index = end;
            continuation = true;
        }
        if (line.isEmpty()) {
            folded.append("\r\n");
        }
        return folded.toString();
    }
}
