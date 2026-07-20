package com.unifiedcalendar.email;

import net.fortuna.ical4j.data.CalendarBuilder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class IcsServiceTest {

    private final IcsService service = new IcsService(Clock.fixed(
            Instant.parse("2024-03-01T12:34:56Z"), ZoneOffset.UTC));

    @Test
    void generatesStableUtcInviteWithCrLfLineEndings() {
        byte[] generated = service.generate(
                "stable-token",
                "Meeting with owner",
                "Visitor: Jane\nPhone: +1, 555; 0100",
                Instant.parse("2024-03-15T14:00:00Z"),
                Instant.parse("2024-03-15T14:30:00Z"),
                "owner@example.com");

        String ics = new String(generated, StandardCharsets.UTF_8);

        assertThat(generated).isNotEmpty();
        assertThat(ics).contains(
                "UID:stable-token@unified-calendar\r\n",
                "DTSTAMP:20240301T123456Z\r\n",
                "DTSTART:20240315T140000Z\r\n",
                "DTEND:20240315T143000Z\r\n",
                "SUMMARY:Meeting with owner\r\n",
                "DESCRIPTION:Visitor: Jane\\nPhone: +1\\, 555\\; 0100\r\n");
        assertThat(ics.replace("\r\n", "")).doesNotContain("\n");

        String regenerated = new String(service.generate(
                "stable-token",
                "Meeting with owner",
                "Visitor: Jane",
                Instant.parse("2024-03-16T14:00:00Z"),
                Instant.parse("2024-03-16T14:30:00Z"),
                "owner@example.com"), StandardCharsets.UTF_8);
        assertThat(regenerated).contains("UID:stable-token@unified-calendar\r\n");
    }

    @Test
    void foldsLongUtf8LinesAtNoMoreThan75Octets() {
        String description = "Notes: " + "é".repeat(80);

        String ics = new String(service.generate(
                "token", "Summary", description,
                Instant.parse("2024-03-15T14:00:00Z"),
                Instant.parse("2024-03-15T14:30:00Z"),
                "owner@example.com"), StandardCharsets.UTF_8);

        assertThat(ics).contains("\r\n ");
        for (String line : ics.split("\r\n")) {
            assertThat(line.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
        }
    }

    @Test
    void generatedInviteParsesAsStandardsCompliantICalendar() throws Exception {
        byte[] generated = service.generate(
                "stable-token",
                "Meeting with owner",
                "Visitor: Jane\nNotes: Café, accessibility; requested " + "矇".repeat(40),
                Instant.parse("2024-03-15T14:00:00Z"),
                Instant.parse("2024-03-15T14:30:00Z"),
                "owner@example.com");

        var calendar = new CalendarBuilder().build(new ByteArrayInputStream(generated));

        assertThat(calendar).isNotNull();
        assertThat(calendar.getComponents()).hasSize(1);
        assertThat(calendar.getComponents().get(0).getName()).isEqualTo("VEVENT");
    }
}
