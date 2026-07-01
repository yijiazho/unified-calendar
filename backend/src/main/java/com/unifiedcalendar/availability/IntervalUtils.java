package com.unifiedcalendar.availability;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure interval arithmetic helpers — no I/O, safe to unit-test in isolation. */
public class IntervalUtils {

    private IntervalUtils() {}

    /** Merges a list of potentially overlapping intervals into a sorted, non-overlapping list. */
    public static List<Interval> merge(List<Interval> intervals) {
        if (intervals.isEmpty()) return List.of();

        List<Interval> sorted = intervals.stream()
                .sorted(Comparator.comparing(Interval::start))
                .toList();

        List<Interval> merged = new ArrayList<>();
        Interval current = sorted.get(0);

        for (int i = 1; i < sorted.size(); i++) {
            Interval next = sorted.get(i);
            if (!next.start().isAfter(current.end())) {
                Instant mergedEnd = current.end().isAfter(next.end()) ? current.end() : next.end();
                current = new Interval(current.start(), mergedEnd);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    /**
     * Subtracts a list of busy intervals from a free window.
     * Busy intervals are clipped to the window before subtraction.
     * Returns the remaining free intervals in the order they appear.
     */
    public static List<Interval> subtract(Interval window, List<Interval> busyIntervals) {
        List<Interval> free = new ArrayList<>();
        free.add(window);

        for (Interval busy : busyIntervals) {
            List<Interval> remaining = new ArrayList<>();
            for (Interval slot : free) {
                Instant busyStart = busy.start().isBefore(slot.start()) ? slot.start() : busy.start();
                Instant busyEnd = busy.end().isAfter(slot.end()) ? slot.end() : busy.end();

                if (!busyStart.isBefore(busyEnd)) {
                    // No overlap with this free slot — keep it intact
                    remaining.add(slot);
                    continue;
                }
                if (slot.start().isBefore(busyStart)) {
                    remaining.add(new Interval(slot.start(), busyStart));
                }
                if (busyEnd.isBefore(slot.end())) {
                    remaining.add(new Interval(busyEnd, slot.end()));
                }
            }
            free = remaining;
        }
        return free;
    }
}
