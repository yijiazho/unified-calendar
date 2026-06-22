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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("JdbcCalendarAccountRepository integration")
class JdbcCalendarAccountRepositoryTest {

    // Isolate this test from OAuth bean initialization — this class tests only the repository.
    @MockBean
    @SuppressWarnings("unused")
    private GoogleOAuthService googleOAuthService;

    @MockBean
    @SuppressWarnings("unused")
    private GoogleTokenRefresher googleTokenRefresher;

    @Autowired
    private JdbcCalendarAccountRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    private Long adminId;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO admins (email, password_hash, slug) VALUES (?, ?, ?)",
                "repo-test@example.com", "hash", "repo-test-slug");
        adminId = jdbc.queryForObject(
                "SELECT id FROM admins WHERE slug = 'repo-test-slug'", Long.class);
    }

    @Test
    @DisplayName("save inserts a new account and findById returns it")
    void saveInsertAndFindById() {
        CalendarAccount account = new CalendarAccount(null, adminId, "GOOGLE", "sub-001",
                "user@gmail.com", "enc_access", "enc_refresh", false, Instant.now(), null);

        CalendarAccount saved = repository.save(account);

        assertNotNull(saved.id(), "id must be assigned after insert");
        Optional<CalendarAccount> found = repository.findById(saved.id(), adminId);
        assertTrue(found.isPresent());
        assertEquals("user@gmail.com", found.get().email());
        assertEquals("enc_access", found.get().encryptedAccessToken());
    }

    @Test
    @DisplayName("save with duplicate (admin_id, provider, provider_account_id) upserts and does not duplicate")
    void saveUpsertDoesNotDuplicate() {
        CalendarAccount first = new CalendarAccount(null, adminId, "GOOGLE", "sub-001",
                "user@gmail.com", "access_v1", "refresh_v1", false, Instant.now(), null);
        repository.save(first);

        CalendarAccount second = new CalendarAccount(null, adminId, "GOOGLE", "sub-001",
                "user@gmail.com", "access_v2", "refresh_v2", false, Instant.now(), null);
        repository.save(second);

        List<CalendarAccount> all = repository.findAllByAdminId(adminId);
        assertEquals(1, all.size(), "upsert must not create a duplicate row");
        assertEquals("access_v2", all.get(0).encryptedAccessToken());
    }

    @Test
    @DisplayName("findAllByAdminId returns only accounts owned by the given admin")
    void findAllByAdminIdIsScopedToAdmin() {
        // Create a second admin
        jdbc.update("INSERT INTO admins (email, password_hash, slug) VALUES (?, ?, ?)",
                "other@example.com", "hash", "other-slug");
        Long otherId = jdbc.queryForObject(
                "SELECT id FROM admins WHERE slug = 'other-slug'", Long.class);

        repository.save(new CalendarAccount(null, adminId, "GOOGLE", "sub-mine",
                "mine@gmail.com", "tok", "ref", false, Instant.now(), null));
        repository.save(new CalendarAccount(null, otherId, "GOOGLE", "sub-other",
                "other@gmail.com", "tok", "ref", false, Instant.now(), null));

        List<CalendarAccount> mine = repository.findAllByAdminId(adminId);
        assertEquals(1, mine.size());
        assertEquals("mine@gmail.com", mine.get(0).email());
    }

    @Test
    @DisplayName("delete removes the account; subsequent findById returns empty")
    void deleteRemovesAccount() {
        CalendarAccount saved = repository.save(new CalendarAccount(null, adminId, "GOOGLE", "sub-del",
                "del@gmail.com", "tok", "ref", false, Instant.now(), null));

        repository.delete(saved.id(), adminId);

        assertTrue(repository.findById(saved.id(), adminId).isEmpty());
    }

    @Test
    @DisplayName("delete is scoped to adminId — cannot delete another admin's account")
    void deleteIsScopedToAdmin() {
        jdbc.update("INSERT INTO admins (email, password_hash, slug) VALUES (?, ?, ?)",
                "attacker@example.com", "hash", "attacker-slug");
        Long attackerId = jdbc.queryForObject(
                "SELECT id FROM admins WHERE slug = 'attacker-slug'", Long.class);

        CalendarAccount saved = repository.save(new CalendarAccount(null, adminId, "GOOGLE", "sub-safe",
                "safe@gmail.com", "tok", "ref", false, Instant.now(), null));

        repository.delete(saved.id(), attackerId);   // wrong adminId — must be a no-op

        assertTrue(repository.findById(saved.id(), adminId).isPresent(),
                "account must still exist after attempted deletion by wrong admin");
    }

    @Test
    @DisplayName("setPrimary marks one account primary and clears all others for that admin")
    void setPrimaryMarksCorrectly() {
        CalendarAccount a1 = repository.save(new CalendarAccount(null, adminId, "GOOGLE", "sub-1",
                "a@gmail.com", "tok", "ref", false, Instant.now(), null));
        CalendarAccount a2 = repository.save(new CalendarAccount(null, adminId, "OUTLOOK", "oid-2",
                "b@outlook.com", "tok", "ref", false, Instant.now(), null));

        repository.setPrimary(a1.id(), adminId);

        assertTrue(repository.findById(a1.id(), adminId).orElseThrow().isPrimary());
        assertFalse(repository.findById(a2.id(), adminId).orElseThrow().isPrimary());

        // Switching primary clears the previous one
        repository.setPrimary(a2.id(), adminId);
        assertFalse(repository.findById(a1.id(), adminId).orElseThrow().isPrimary());
        assertTrue(repository.findById(a2.id(), adminId).orElseThrow().isPrimary());
    }

    @Test
    @DisplayName("delete cascades to calendar_events via FK — PRAGMA foreign_keys must be ON")
    void deleteAccountCascadesToEvents() {
        CalendarAccount account = repository.save(new CalendarAccount(null, adminId, "GOOGLE", "sub-cascade",
                "cascade@gmail.com", "tok", "ref", false, Instant.now(), null));

        jdbc.update(
                "INSERT INTO calendar_events " +
                "(admin_id, calendar_account_id, provider, provider_event_id, title, start_time_utc, end_time_utc) " +
                "VALUES (?, ?, 'GOOGLE', 'evt-001', 'Test Event', '2024-01-01T09:00:00Z', '2024-01-01T10:00:00Z')",
                adminId, account.id());
        Integer eventCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM calendar_events WHERE calendar_account_id = ?",
                Integer.class, account.id());
        assertEquals(1, eventCount, "event must exist before account deletion");

        repository.delete(account.id(), adminId);

        Integer afterCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM calendar_events WHERE calendar_account_id = ?",
                Integer.class, account.id());
        assertEquals(0, afterCount, "calendar_events must be cascade-deleted when account is deleted");
    }

    @Test
    @DisplayName("save with non-null id updates the existing row")
    void saveWithIdUpdatesRow() {
        CalendarAccount inserted = repository.save(new CalendarAccount(null, adminId, "GOOGLE", "sub-upd",
                "upd@gmail.com", "old_access", "old_refresh", false, Instant.now(), null));

        CalendarAccount updated = new CalendarAccount(inserted.id(), adminId, "GOOGLE", "sub-upd",
                "upd@gmail.com", "new_access", "new_refresh", false, inserted.connectedAt(), null);
        repository.save(updated);

        CalendarAccount found = repository.findById(inserted.id(), adminId).orElseThrow();
        assertEquals("new_access", found.encryptedAccessToken());
        assertEquals("new_refresh", found.encryptedRefreshToken());
    }
}
