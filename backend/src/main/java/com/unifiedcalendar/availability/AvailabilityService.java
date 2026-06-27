package com.unifiedcalendar.availability;

import com.unifiedcalendar.auth.Admin;
import com.unifiedcalendar.auth.AdminRepository;
import com.unifiedcalendar.calendar.CalendarEvent;
import com.unifiedcalendar.calendar.CalendarEventRepository;
import com.unifiedcalendar.workinghours.WorkingHours;
import com.unifiedcalendar.workinghours.WorkingHoursRepository;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class AvailabilityService {

    private final WorkingHoursRepository workingHoursRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final AdminRepository adminRepository;

    public AvailabilityService(
            WorkingHoursRepository workingHoursRepository,
            CalendarEventRepository calendarEventRepository,
            AdminRepository adminRepository) {
        this.workingHoursRepository = workingHoursRepository;
        this.calendarEventRepository = calendarEventRepository;
        this.adminRepository = adminRepository;
    }

    /**
     * Computes available 30-minute booking slots for the given admin and date.
     * Slots that have already started are excluded. Never calls provider APIs.
     */
    public List<TimeSlot> getAvailableSlots(Long adminId, LocalDate date) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found: " + adminId));
        return getAvailableSlots(admin, date);
    }

    /** Returns true if the specific slot appears in the computed available slots for that day. */
    public boolean isSlotAvailable(Long adminId, Instant slotStart, Instant slotEnd) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found: " + adminId));
        LocalDate date = slotStart.atZone(resolveTimezone(admin)).toLocalDate();
        return getAvailableSlots(admin, date).stream()
                .anyMatch(s -> s.start().equals(slotStart) && s.end().equals(slotEnd));
    }

    private List<TimeSlot> getAvailableSlots(Admin admin, LocalDate date) {
        ZoneId tz = resolveTimezone(admin);

        // dayOfWeek: 0=Monday ... 6=Sunday (matches working_hours.day_of_week convention)
        int dayOfWeek = date.getDayOfWeek().ordinal();
        Optional<WorkingHours> hoursOpt = workingHoursRepository.findAllByAdminId(admin.id())
                .stream()
                .filter(wh -> wh.dayOfWeek() == dayOfWeek)
                .findFirst();
        if (hoursOpt.isEmpty()) {
            return List.of();
        }

        WorkingHours hours = hoursOpt.get();
        Instant workStart = LocalDateTime.of(date, LocalTime.parse(hours.startTime()))
                .atZone(tz).toInstant();
        Instant workEnd = LocalDateTime.of(date, LocalTime.parse(hours.endTime()))
                .atZone(tz).toInstant();

        List<CalendarEvent> busyEvents = calendarEventRepository
                .findByAdminIdAndTimeRange(admin.id(), workStart, workEnd);

        List<Interval> busyIntervals = busyEvents.stream()
                .map(e -> new Interval(e.startTimeUtc(), e.endTimeUtc()))
                .toList();
        List<Interval> mergedBusy = IntervalUtils.merge(busyIntervals);
        List<Interval> freeIntervals = IntervalUtils.subtract(new Interval(workStart, workEnd), mergedBusy);

        Instant now = Instant.now();
        List<TimeSlot> slots = new ArrayList<>();
        for (Interval free : freeIntervals) {
            Instant slotStart = free.start();
            while (true) {
                Instant slotEnd = slotStart.plus(30, ChronoUnit.MINUTES);
                if (slotEnd.isAfter(free.end())) break;
                if (!slotStart.isBefore(now)) {
                    slots.add(new TimeSlot(slotStart, slotEnd));
                }
                slotStart = slotEnd;
            }
        }
        return slots;
    }

    /** Resolves the admin's stored timezone string, failing fast with an actionable message on bad data. */
    private ZoneId resolveTimezone(Admin admin) {
        try {
            return ZoneId.of(admin.timezone());
        } catch (DateTimeException e) {
            throw new IllegalStateException(
                    "Admin " + admin.id() + " has invalid timezone '" + admin.timezone() +
                    "' — use IANA format (e.g. America/New_York)", e);
        }
    }
}
