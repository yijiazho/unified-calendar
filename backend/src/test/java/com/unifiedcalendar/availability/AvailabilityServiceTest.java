package com.unifiedcalendar.availability;

import com.unifiedcalendar.auth.Admin;
import com.unifiedcalendar.auth.AuthService;
import com.unifiedcalendar.calendar.CalendarEvent;
import com.unifiedcalendar.calendar.CalendarEventRepository;
import com.unifiedcalendar.calendar.GoogleOAuthService;
import com.unifiedcalendar.calendar.GoogleTokenRefresher;
import com.unifiedcalendar.calendar.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("AvailabilityService")
class AvailabilityServiceTest {

    // Far-future date so Instant.now() never filters out slots in happy-path tests
    private static final LocalDate FUTURE_DATE = LocalDate.of(2099, 6, 1);
    // dayOfWeek: 0=Monday ... 6=Sunday — derived at runtime to avoid hard-coding the weekday
    private static final int FUTURE_DAY_OF_WEEK = FUTURE_DATE.getDayOfWeek().ordinal();

    // Past date used only for the past-slot exclusion test
    private static final LocalDate PAST_DATE = LocalDate.of(2020, 1, 6); // Monday
    private static final int PAST_DAY_OF_WEEK = PAST_DATE.getDayOfWeek().ordinal();

    // Suppressed to prevent Spring from attempting live OAuth network calls during context startup.
    // Removing these mocks causes the test context to fail with a connection error, not a compile error.
    @MockBean
    @SuppressWarnings("unused")
    private GoogleOAuthService googleOAuthService;

    @MockBean
    @SuppressWarnings("unused")
    private GoogleTokenRefresher googleTokenRefresher;

    @Autowired
    private AvailabilityService availabilityService;

    @Autowired
    private AuthService authService;

    @Autowired
    private CalendarEventRepository calendarEventRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private Long adminId;
    private Long accountId;

    @BeforeEach
    void setUp() {
        Admin admin = authService.signup("avail-test@example.com", "pw", "avail-slug", "UTC");
        adminId = admin.id();
        jdbc.update(
                "INSERT INTO calendar_accounts " +
                "(admin_id, provider, provider_account_id, email, encrypted_access_token, encrypted_refresh_token) " +
                "VALUES (?, 'GOOGLE', 'sub-avail', 'avail@gmail.com', 'enc', 'enc')",
                adminId);
        accountId = jdbc.queryForObject(
                "SELECT id FROM calendar_accounts WHERE admin_id = ?", Long.class, adminId);
    }

    private void seedWorkingHours(int dayOfWeek, String start, String end) {
        jdbc.update("INSERT INTO working_hours (admin_id, day_of_week, start_time, end_time) VALUES (?, ?, ?, ?)",
                adminId, dayOfWeek, start, end);
    }

    private void seedEvent(String providerEventId, String start, String end) {
        calendarEventRepository.upsert(new CalendarEvent(
                null, adminId, accountId, Provider.GOOGLE, providerEventId,
                "Test Event", Instant.parse(start), Instant.parse(end),
                false, null, null));
    }

    @Test
    @DisplayName("returns empty list when no working hours configured for the day")
    void noWorkingHoursReturnsEmpty() {
        List<TimeSlot> slots = availabilityService.getAvailableSlots(adminId, FUTURE_DATE);
        assertTrue(slots.isEmpty());
    }

    @Test
    @DisplayName("09:00–17:00 UTC with no events returns 16 slots")
    void fullDayNoEventsReturns16Slots() {
        seedWorkingHours(FUTURE_DAY_OF_WEEK, "09:00", "17:00");

        List<TimeSlot> slots = availabilityService.getAvailableSlots(adminId, FUTURE_DATE);

        assertEquals(16, slots.size());
        assertEquals(Instant.parse("2099-06-01T09:00:00Z"), slots.get(0).start());
        assertEquals(Instant.parse("2099-06-01T09:30:00Z"), slots.get(0).end());
        assertEquals(Instant.parse("2099-06-01T16:30:00Z"), slots.get(15).start());
        assertEquals(Instant.parse("2099-06-01T17:00:00Z"), slots.get(15).end());
    }

    @Test
    @DisplayName("event 10:00–11:00 removes two slots, leaving 14")
    void oneEventRemovesTwoSlots() {
        seedWorkingHours(FUTURE_DAY_OF_WEEK, "09:00", "17:00");
        seedEvent("evt-001", "2099-06-01T10:00:00Z", "2099-06-01T11:00:00Z");

        List<TimeSlot> slots = availabilityService.getAvailableSlots(adminId, FUTURE_DATE);

        assertEquals(14, slots.size());
        // 10:00 and 10:30 slots must be absent
        slots.forEach(s -> assertFalse(
                s.start().equals(Instant.parse("2099-06-01T10:00:00Z")) ||
                s.start().equals(Instant.parse("2099-06-01T10:30:00Z")),
                "slot at " + s.start() + " should not be present"));
    }

    @Test
    @DisplayName("overlapping events 10:00–11:00 and 10:30–12:00 are merged before subtraction")
    void overlappingEventsAreMerged() {
        seedWorkingHours(FUTURE_DAY_OF_WEEK, "09:00", "17:00");
        seedEvent("evt-a", "2099-06-01T10:00:00Z", "2099-06-01T11:00:00Z");
        seedEvent("evt-b", "2099-06-01T10:30:00Z", "2099-06-01T12:00:00Z");

        List<TimeSlot> slots = availabilityService.getAvailableSlots(adminId, FUTURE_DATE);

        // Free windows: 09:00–10:00 (2 slots) and 12:00–17:00 (10 slots) = 12
        assertEquals(12, slots.size());
    }

    @Test
    @DisplayName("isSlotAvailable returns false when slot overlaps a busy event")
    void isSlotAvailableReturnsFalseForBusySlot() {
        seedWorkingHours(FUTURE_DAY_OF_WEEK, "09:00", "17:00");
        seedEvent("evt-busy", "2099-06-01T10:00:00Z", "2099-06-01T11:00:00Z");

        boolean available = availabilityService.isSlotAvailable(
                adminId,
                Instant.parse("2099-06-01T10:00:00Z"),
                Instant.parse("2099-06-01T10:30:00Z"));

        assertFalse(available);
    }

    @Test
    @DisplayName("isSlotAvailable returns true for a slot in a free window")
    void isSlotAvailableReturnsTrueForFreeSlot() {
        seedWorkingHours(FUTURE_DAY_OF_WEEK, "09:00", "17:00");

        boolean available = availabilityService.isSlotAvailable(
                adminId,
                Instant.parse("2099-06-01T09:00:00Z"),
                Instant.parse("2099-06-01T09:30:00Z"));

        assertTrue(available);
    }

    @Test
    @DisplayName("returns empty list when all working hours are covered by events")
    void allDayBusyReturnsEmpty() {
        seedWorkingHours(FUTURE_DAY_OF_WEEK, "09:00", "17:00");
        seedEvent("evt-all", "2099-06-01T08:00:00Z", "2099-06-01T18:00:00Z");

        List<TimeSlot> slots = availabilityService.getAvailableSlots(adminId, FUTURE_DATE);

        assertTrue(slots.isEmpty());
    }

    @Test
    @DisplayName("busy event starting before working window is clipped to window start")
    void busyStartingBeforeWindowIsClipped() {
        seedWorkingHours(FUTURE_DAY_OF_WEEK, "09:00", "17:00");
        // Covers 07:00–10:00; should block 09:00 and 09:30 slots only
        seedEvent("evt-early", "2099-06-01T07:00:00Z", "2099-06-01T10:00:00Z");

        List<TimeSlot> slots = availabilityService.getAvailableSlots(adminId, FUTURE_DATE);

        // 09:00 and 09:30 are busy; 10:00 onward is free (14 slots)
        assertEquals(14, slots.size());
        assertEquals(Instant.parse("2099-06-01T10:00:00Z"), slots.get(0).start());
    }

    @Test
    @DisplayName("busy event ending after working window is clipped to window end")
    void busyEndingAfterWindowIsClipped() {
        seedWorkingHours(FUTURE_DAY_OF_WEEK, "09:00", "17:00");
        // Covers 16:00–20:00; should block 16:00 and 16:30 slots only
        seedEvent("evt-late", "2099-06-01T16:00:00Z", "2099-06-01T20:00:00Z");

        List<TimeSlot> slots = availabilityService.getAvailableSlots(adminId, FUTURE_DATE);

        // 09:00–15:30 free (14 slots); 16:00 and 16:30 busy
        assertEquals(14, slots.size());
        assertEquals(Instant.parse("2099-06-01T15:30:00Z"), slots.get(slots.size() - 1).start());
    }

    @Test
    @DisplayName("all slots in the past are excluded")
    void pastSlotsAreExcluded() {
        seedWorkingHours(PAST_DAY_OF_WEEK, "09:00", "17:00");

        List<TimeSlot> slots = availabilityService.getAvailableSlots(adminId, PAST_DATE);

        assertTrue(slots.isEmpty(), "slots from a past date must all be excluded");
    }

    @Test
    @DisplayName("free interval shorter than 30 minutes generates no slot")
    void freeIntervalShorterThan30MinGeneratesNoSlot() {
        // Working window is only 20 minutes
        seedWorkingHours(FUTURE_DAY_OF_WEEK, "09:00", "09:20");

        List<TimeSlot> slots = availabilityService.getAvailableSlots(adminId, FUTURE_DATE);

        assertTrue(slots.isEmpty());
    }
}
