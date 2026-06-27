package com.unifiedcalendar.availability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("IntervalUtils")
class IntervalUtilsTest {

    private static Interval i(String start, String end) {
        return new Interval(Instant.parse(start), Instant.parse(end));
    }

    // ── merge ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("merge returns empty list for empty input")
    void mergeEmpty() {
        assertTrue(IntervalUtils.merge(List.of()).isEmpty());
    }

    @Test
    @DisplayName("merge returns single interval unchanged")
    void mergeSingleInterval() {
        List<Interval> result = IntervalUtils.merge(List.of(i("2024-01-01T09:00:00Z", "2024-01-01T17:00:00Z")));
        assertEquals(1, result.size());
        assertEquals(Instant.parse("2024-01-01T09:00:00Z"), result.get(0).start());
        assertEquals(Instant.parse("2024-01-01T17:00:00Z"), result.get(0).end());
    }

    @Test
    @DisplayName("merge keeps non-overlapping intervals separate")
    void mergeNonOverlapping() {
        List<Interval> result = IntervalUtils.merge(List.of(
                i("2024-01-01T09:00:00Z", "2024-01-01T10:00:00Z"),
                i("2024-01-01T11:00:00Z", "2024-01-01T12:00:00Z")
        ));
        assertEquals(2, result.size());
        assertEquals(Instant.parse("2024-01-01T09:00:00Z"), result.get(0).start());
        assertEquals(Instant.parse("2024-01-01T12:00:00Z"), result.get(1).end());
    }

    @Test
    @DisplayName("merge collapses two overlapping intervals into one")
    void mergeTwoOverlapping() {
        List<Interval> result = IntervalUtils.merge(List.of(
                i("2024-01-01T10:00:00Z", "2024-01-01T11:00:00Z"),
                i("2024-01-01T10:30:00Z", "2024-01-01T12:00:00Z")
        ));
        assertEquals(1, result.size());
        assertEquals(Instant.parse("2024-01-01T10:00:00Z"), result.get(0).start());
        assertEquals(Instant.parse("2024-01-01T12:00:00Z"), result.get(0).end());
    }

    @Test
    @DisplayName("merge handles unsorted input by sorting before merging")
    void mergeUnsortedInput() {
        List<Interval> result = IntervalUtils.merge(List.of(
                i("2024-01-01T11:00:00Z", "2024-01-01T12:00:00Z"),
                i("2024-01-01T09:00:00Z", "2024-01-01T10:00:00Z")
        ));
        assertEquals(2, result.size());
        assertEquals(Instant.parse("2024-01-01T09:00:00Z"), result.get(0).start());
        assertEquals(Instant.parse("2024-01-01T11:00:00Z"), result.get(1).start());
    }

    @Test
    @DisplayName("merge extends to the furthest end when one interval contains another")
    void mergeContainedInterval() {
        List<Interval> result = IntervalUtils.merge(List.of(
                i("2024-01-01T09:00:00Z", "2024-01-01T17:00:00Z"),
                i("2024-01-01T10:00:00Z", "2024-01-01T11:00:00Z")
        ));
        assertEquals(1, result.size());
        assertEquals(Instant.parse("2024-01-01T09:00:00Z"), result.get(0).start());
        assertEquals(Instant.parse("2024-01-01T17:00:00Z"), result.get(0).end());
    }

    // ── subtract ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("subtract with no busy intervals returns the full window")
    void subtractNoBusy() {
        Interval window = i("2024-01-01T09:00:00Z", "2024-01-01T17:00:00Z");
        List<Interval> result = IntervalUtils.subtract(window, List.of());
        assertEquals(1, result.size());
        assertEquals(window, result.get(0));
    }

    @Test
    @DisplayName("subtract removes a middle busy interval leaving two free intervals")
    void subtractMiddleBusy() {
        Interval window = i("2024-01-01T09:00:00Z", "2024-01-01T17:00:00Z");
        List<Interval> result = IntervalUtils.subtract(window,
                List.of(i("2024-01-01T10:00:00Z", "2024-01-01T11:00:00Z")));

        assertEquals(2, result.size());
        assertEquals(Instant.parse("2024-01-01T09:00:00Z"), result.get(0).start());
        assertEquals(Instant.parse("2024-01-01T10:00:00Z"), result.get(0).end());
        assertEquals(Instant.parse("2024-01-01T11:00:00Z"), result.get(1).start());
        assertEquals(Instant.parse("2024-01-01T17:00:00Z"), result.get(1).end());
    }

    @Test
    @DisplayName("subtract returns empty list when busy covers the full window")
    void subtractFullyCovered() {
        Interval window = i("2024-01-01T09:00:00Z", "2024-01-01T17:00:00Z");
        List<Interval> result = IntervalUtils.subtract(window,
                List.of(i("2024-01-01T09:00:00Z", "2024-01-01T17:00:00Z")));
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("subtract clips busy interval that starts before the working window")
    void subtractBusyStartsBeforeWindow() {
        Interval window = i("2024-01-01T09:00:00Z", "2024-01-01T17:00:00Z");
        List<Interval> result = IntervalUtils.subtract(window,
                List.of(i("2024-01-01T07:00:00Z", "2024-01-01T10:00:00Z")));

        assertEquals(1, result.size());
        assertEquals(Instant.parse("2024-01-01T10:00:00Z"), result.get(0).start());
        assertEquals(Instant.parse("2024-01-01T17:00:00Z"), result.get(0).end());
    }

    @Test
    @DisplayName("subtract clips busy interval that ends after the working window")
    void subtractBusyEndsAfterWindow() {
        Interval window = i("2024-01-01T09:00:00Z", "2024-01-01T17:00:00Z");
        List<Interval> result = IntervalUtils.subtract(window,
                List.of(i("2024-01-01T16:00:00Z", "2024-01-01T20:00:00Z")));

        assertEquals(1, result.size());
        assertEquals(Instant.parse("2024-01-01T09:00:00Z"), result.get(0).start());
        assertEquals(Instant.parse("2024-01-01T16:00:00Z"), result.get(0).end());
    }

    @Test
    @DisplayName("subtract with non-overlapping busy interval leaves window unchanged")
    void subtractNonOverlappingBusy() {
        Interval window = i("2024-01-01T09:00:00Z", "2024-01-01T17:00:00Z");
        List<Interval> result = IntervalUtils.subtract(window,
                List.of(i("2024-01-01T18:00:00Z", "2024-01-01T19:00:00Z")));

        assertEquals(1, result.size());
        assertEquals(window, result.get(0));
    }

    @Test
    @DisplayName("subtract handles multiple non-adjacent busy intervals correctly")
    void subtractMultipleBusyIntervals() {
        Interval window = i("2024-01-01T09:00:00Z", "2024-01-01T17:00:00Z");
        List<Interval> result = IntervalUtils.subtract(window, List.of(
                i("2024-01-01T10:00:00Z", "2024-01-01T11:00:00Z"),
                i("2024-01-01T13:00:00Z", "2024-01-01T14:00:00Z")
        ));

        assertEquals(3, result.size());
        assertEquals(Instant.parse("2024-01-01T09:00:00Z"), result.get(0).start());
        assertEquals(Instant.parse("2024-01-01T10:00:00Z"), result.get(0).end());
        assertEquals(Instant.parse("2024-01-01T11:00:00Z"), result.get(1).start());
        assertEquals(Instant.parse("2024-01-01T13:00:00Z"), result.get(1).end());
        assertEquals(Instant.parse("2024-01-01T14:00:00Z"), result.get(2).start());
        assertEquals(Instant.parse("2024-01-01T17:00:00Z"), result.get(2).end());
    }
}
