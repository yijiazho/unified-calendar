package com.unifiedcalendar;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Schema integration")
class SchemaIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private static final List<String> EXPECTED_TABLES = List.of(
            "admins", "calendar_accounts", "working_hours", "calendar_events", "bookings"
    );

    @Test
    @DisplayName("all five tables exist in sqlite_master")
    void allTablesExist() {
        for (String table : EXPECTED_TABLES) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?",
                    Integer.class, table);
            assertEquals(1, count, "Missing table: " + table);
        }
    }

    @Test
    @DisplayName("calendar_events.provider rejects values outside ('GOOGLE','OUTLOOK')")
    void calendarEventsProviderCheckConstraint() {
        jdbc.execute("INSERT INTO admins (email, password_hash, slug) VALUES ('chk@example.com', 'hash', 'chk-slug')");
        Integer adminId = jdbc.queryForObject(
                "SELECT id FROM admins WHERE slug='chk-slug'", Integer.class);
        jdbc.execute("INSERT INTO calendar_accounts " +
                "(admin_id, provider, provider_account_id, email, encrypted_access_token, encrypted_refresh_token) " +
                "VALUES (" + adminId + ", 'GOOGLE', 'sub1', 'chk@example.com', 'tok', 'ref')");
        Integer accountId = jdbc.queryForObject(
                "SELECT id FROM calendar_accounts WHERE admin_id=" + adminId, Integer.class);

        assertThrows(Exception.class, () ->
                jdbc.execute("INSERT INTO calendar_events " +
                        "(admin_id, calendar_account_id, provider, provider_event_id, start_time_utc, end_time_utc) " +
                        "VALUES (" + adminId + ", " + accountId + ", 'google', 'evt1', " +
                        "'2024-01-01T09:00:00Z', '2024-01-01T10:00:00Z')"),
                "CHECK constraint should reject provider='google'");
    }

    @Test
    @DisplayName("bookings.cancel_token uniqueness constraint is enforced")
    void bookingCancelTokenUniqueness() {
        jdbc.execute("INSERT INTO admins (email, password_hash, slug) VALUES ('tok@example.com', 'hash', 'tok-slug')");
        Integer adminId = jdbc.queryForObject(
                "SELECT id FROM admins WHERE slug='tok-slug'", Integer.class);

        jdbc.execute("INSERT INTO bookings (admin_id, visitor_name, visitor_email, cancel_token, reschedule_token) " +
                "VALUES (" + adminId + ", 'Alice', 'alice@example.com', 'cancel-dup', 'resched-1')");

        assertThrows(Exception.class, () ->
                jdbc.execute("INSERT INTO bookings (admin_id, visitor_name, visitor_email, cancel_token, reschedule_token) " +
                        "VALUES (" + adminId + ", 'Bob', 'bob@example.com', 'cancel-dup', 'resched-2')"),
                "UNIQUE constraint should reject duplicate cancel_token");
    }

    @Test
    @DisplayName("admins.updated_at is refreshed by the AFTER UPDATE trigger")
    void adminUpdatedAtTriggerFires() {
        jdbc.execute("INSERT INTO admins (email, password_hash, slug) VALUES ('trg@example.com', 'hash', 'trg-slug')");
        // Force updated_at to a known past value so any trigger-driven update is detectable
        jdbc.execute("UPDATE admins SET updated_at='2000-01-01T00:00:00Z' WHERE slug='trg-slug'");
        jdbc.execute("UPDATE admins SET timezone='America/New_York' WHERE slug='trg-slug'");

        String updatedAt = jdbc.queryForObject(
                "SELECT updated_at FROM admins WHERE slug='trg-slug'", String.class);
        assertNotEquals("2000-01-01T00:00:00Z", updatedAt,
                "AFTER UPDATE trigger should have refreshed updated_at");
    }
}
