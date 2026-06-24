package com.unifiedcalendar.calendar;

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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("JdbcCalendarEventRepository integration")
class JdbcCalendarEventRepositoryTest {

    @MockBean
    @SuppressWarnings("unused")
    private GoogleOAuthService googleOAuthService;

    @MockBean
    @SuppressWarnings("unused")
    private GoogleTokenRefresher googleTokenRefresher;

    @Autowired
    private JdbcCalendarEventRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    private Long adminId;
    private Long accountId;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO admins (email, password_hash, slug) VALUES (?, ?, ?)",
                "event-test@example.com", "hash", "event-test-slug");
        adminId = jdbc.queryForObject("SELECT id FROM admins WHERE slug = 'event-test-slug'", Long.class);
        jdbc.update(
                "INSERT INTO calendar_accounts " +
                "(admin_id, provider, provider_account_id, email, encrypted_access_token, encrypted_refresh_token) " +
                "VALUES (?, 'GOOGLE', 'sub-001', 'test@gmail.com', 'enc_access', 'enc_refresh')",
                adminId);
        accountId = jdbc.queryForObject(
                "SELECT id FROM calendar_accounts WHERE admin_id = ?", Long.class, adminId);
    }

    private CalendarEvent event(String providerEventId, Instant start, Instant end) {
        return new CalendarEvent(null, adminId, accountId, "GOOGLE", providerEventId,
                "Test Event", start, end, false, null, null);
    }

    @Test
    @DisplayName("upsert inserts a new event")
    void upsertInserts() {
        repository.upsert(event("evt-001",
                Instant.parse("2024-06-01T09:00:00Z"), Instant.parse("2024-06-01T10:00:00Z")));

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM calendar_events WHERE provider_event_id = 'evt-001'", Integer.class);
        assertEquals(1, count);
    }

    @Test
    @DisplayName("upsert on duplicate (calendar_account_id, provider_event_id) updates the existing row")
    void upsertUpdatesExistingRow() {
        repository.upsert(event("evt-dup",
                Instant.parse("2024-06-01T09:00:00Z"), Instant.parse("2024-06-01T10:00:00Z")));
        repository.upsert(new CalendarEvent(null, adminId, accountId, "GOOGLE", "evt-dup",
                "Updated Title",
                Instant.parse("2024-06-01T10:00:00Z"), Instant.parse("2024-06-01T11:00:00Z"),
                false, null, null));

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM calendar_events WHERE provider_event_id = 'evt-dup'", Integer.class);
        assertEquals(1, count, "upsert must not create a duplicate row");

        String title = jdbc.queryForObject(
                "SELECT title FROM calendar_events WHERE provider_event_id = 'evt-dup'", String.class);
        assertEquals("Updated Title", title);
    }

    @Test
    @DisplayName("deleteByCalendarAccountIdAndProviderEventIdNotIn removes only stale events")
    void deleteNotInRemovesStaleEvents() {
        repository.upsert(event("evt-keep",
                Instant.parse("2024-06-01T09:00:00Z"), Instant.parse("2024-06-01T10:00:00Z")));
        repository.upsert(event("evt-gone",
                Instant.parse("2024-06-01T11:00:00Z"), Instant.parse("2024-06-01T12:00:00Z")));

        repository.deleteByCalendarAccountIdAndProviderEventIdNotIn(accountId, List.of("evt-keep"));

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM calendar_events WHERE provider_event_id = 'evt-keep'", Integer.class));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM calendar_events WHERE provider_event_id = 'evt-gone'", Integer.class));
    }

    @Test
    @DisplayName("deleteByCalendarAccountIdAndProviderEventIdNotIn with empty list deletes all account events")
    void deleteNotInWithEmptyListDeletesAll() {
        repository.upsert(event("evt-a",
                Instant.parse("2024-06-01T09:00:00Z"), Instant.parse("2024-06-01T10:00:00Z")));
        repository.upsert(event("evt-b",
                Instant.parse("2024-06-01T11:00:00Z"), Instant.parse("2024-06-01T12:00:00Z")));

        repository.deleteByCalendarAccountIdAndProviderEventIdNotIn(accountId, List.of());

        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM calendar_events WHERE calendar_account_id = ?",
                Integer.class, accountId));
    }

    @Test
    @DisplayName("findByAdminIdAndTimeRange returns events that overlap the window")
    void findByAdminIdAndTimeRangeReturnsOverlapping() {
        // Overlaps [10:00, 12:00]: starts at 09:00, ends at 11:00
        repository.upsert(event("in-range",
                Instant.parse("2024-06-01T09:00:00Z"), Instant.parse("2024-06-01T11:00:00Z")));
        // Entirely before the window
        repository.upsert(event("before",
                Instant.parse("2024-06-01T07:00:00Z"), Instant.parse("2024-06-01T09:00:00Z")));
        // Entirely after the window
        repository.upsert(event("after",
                Instant.parse("2024-06-01T13:00:00Z"), Instant.parse("2024-06-01T14:00:00Z")));

        List<CalendarEvent> found = repository.findByAdminIdAndTimeRange(
                adminId,
                Instant.parse("2024-06-01T10:00:00Z"),
                Instant.parse("2024-06-01T12:00:00Z"));

        assertEquals(1, found.size());
        assertEquals("in-range", found.get(0).providerEventId());
    }

    @Test
    @DisplayName("findByAdminIdAndTimeRange is scoped to the requesting admin")
    void findByAdminIdAndTimeRangeScopedToAdmin() {
        jdbc.update("INSERT INTO admins (email, password_hash, slug) VALUES (?, ?, ?)",
                "other@example.com", "hash", "other-slug");
        Long otherAdminId = jdbc.queryForObject(
                "SELECT id FROM admins WHERE slug = 'other-slug'", Long.class);
        jdbc.update(
                "INSERT INTO calendar_accounts " +
                "(admin_id, provider, provider_account_id, email, encrypted_access_token, encrypted_refresh_token) " +
                "VALUES (?, 'GOOGLE', 'sub-other', 'other@gmail.com', 'enc', 'enc')",
                otherAdminId);
        Long otherAccountId = jdbc.queryForObject(
                "SELECT id FROM calendar_accounts WHERE admin_id = ?", Long.class, otherAdminId);

        repository.upsert(event("my-event",
                Instant.parse("2024-06-01T09:00:00Z"), Instant.parse("2024-06-01T10:00:00Z")));
        jdbc.update(
                "INSERT INTO calendar_events " +
                "(admin_id, calendar_account_id, provider, provider_event_id, title, start_time_utc, end_time_utc) " +
                "VALUES (?, ?, 'GOOGLE', 'other-event', 'Other', '2024-06-01T09:00:00Z', '2024-06-01T10:00:00Z')",
                otherAdminId, otherAccountId);

        List<CalendarEvent> found = repository.findByAdminIdAndTimeRange(
                adminId,
                Instant.parse("2024-06-01T08:00:00Z"),
                Instant.parse("2024-06-01T11:00:00Z"));

        assertEquals(1, found.size());
        assertEquals("my-event", found.get(0).providerEventId());
    }

    @Test
    @DisplayName("findWithEmailByAdminIdAndTimeRange returns events with calendar email joined")
    void findWithEmailReturnsEmail() {
        repository.upsert(event("evt-email",
                Instant.parse("2024-06-01T09:00:00Z"), Instant.parse("2024-06-01T10:00:00Z")));

        List<CalendarEventResponse> found = repository.findWithEmailByAdminIdAndTimeRange(
                adminId,
                Instant.parse("2024-06-01T08:00:00Z"),
                Instant.parse("2024-06-01T11:00:00Z"));

        assertEquals(1, found.size());
        CalendarEventResponse resp = found.get(0);
        assertEquals("test@gmail.com", resp.calendarEmail());
        assertEquals("GOOGLE", resp.provider());
        assertEquals(accountId, resp.calendarAccountId());
    }

    @Test
    @DisplayName("findWithEmailByAdminIdAndTimeRange excludes events outside range")
    void findWithEmailExcludesOutOfRange() {
        // Entirely before the window — ends at 08:59:59 which is before the 09:00 start
        repository.upsert(event("before",
                Instant.parse("2024-06-01T07:00:00Z"), Instant.parse("2024-06-01T08:59:59Z")));
        // Overlapping — starts before, ends inside the window
        repository.upsert(event("overlaps",
                Instant.parse("2024-06-01T08:00:00Z"), Instant.parse("2024-06-01T10:00:00Z")));

        List<CalendarEventResponse> found = repository.findWithEmailByAdminIdAndTimeRange(
                adminId,
                Instant.parse("2024-06-01T09:00:00Z"),
                Instant.parse("2024-06-01T11:00:00Z"));

        assertEquals(1, found.size());
        assertEquals("2024-06-01T08:00:00Z", found.get(0).start());
    }

    @Test
    @DisplayName("findWithEmailByAdminIdAndTimeRange returns empty list when no events match")
    void findWithEmailReturnsEmptyListWhenNoMatch() {
        List<CalendarEventResponse> found = repository.findWithEmailByAdminIdAndTimeRange(
                adminId,
                Instant.parse("2024-06-01T00:00:00Z"),
                Instant.parse("2024-06-01T23:59:59Z"));

        assertTrue(found.isEmpty());
    }

    @Test
    @DisplayName("findWithEmailByAdminIdAndTimeRange reflects isBookingEvent flag correctly")
    void findWithEmailReflectsBookingEventFlag() {
        jdbc.update(
                "INSERT INTO calendar_events " +
                "(admin_id, calendar_account_id, provider, provider_event_id, title, " +
                " start_time_utc, end_time_utc, is_booking_event) " +
                "VALUES (?, ?, 'GOOGLE', 'booking-001', 'Booked Slot', " +
                " '2024-06-01T10:00:00Z', '2024-06-01T10:30:00Z', 1)",
                adminId, accountId);

        List<CalendarEventResponse> found = repository.findWithEmailByAdminIdAndTimeRange(
                adminId,
                Instant.parse("2024-06-01T09:00:00Z"),
                Instant.parse("2024-06-01T11:00:00Z"));

        assertEquals(1, found.size());
        assertTrue(found.get(0).isBookingEvent());
    }

    @Test
    @DisplayName("upsert preserves is_booking_event flag on conflict update")
    void upsertPreservesBookingEventFlag() {
        // Insert a booking event directly (simulating a booking flow)
        jdbc.update(
                "INSERT INTO calendar_events " +
                "(admin_id, calendar_account_id, provider, provider_event_id, title, " +
                " start_time_utc, end_time_utc, is_booking_event) " +
                "VALUES (?, ?, 'GOOGLE', 'booking-evt', 'Booking', " +
                " '2024-06-01T09:00:00Z', '2024-06-01T10:00:00Z', 1)",
                adminId, accountId);

        // Sync upsert should update title/times but not touch is_booking_event
        repository.upsert(new CalendarEvent(null, adminId, accountId, "GOOGLE", "booking-evt",
                "Booking Updated",
                Instant.parse("2024-06-01T09:00:00Z"), Instant.parse("2024-06-01T10:00:00Z"),
                false, null, null));

        // is_booking_event must still be 1 — the ON CONFLICT clause does not update it
        Integer flag = jdbc.queryForObject(
                "SELECT is_booking_event FROM calendar_events WHERE provider_event_id = 'booking-evt'",
                Integer.class);
        assertEquals(1, flag, "sync upsert must not overwrite is_booking_event");
    }
}
