package com.unifiedcalendar.calendar;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcCalendarAccountRepository implements CalendarAccountRepository {

    private final JdbcTemplate jdbc;

    public JdbcCalendarAccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<CalendarAccount> ROW_MAPPER = (rs, rowNum) -> new CalendarAccount(
            rs.getLong("id"),
            rs.getLong("admin_id"),
            rs.getString("provider"),
            rs.getString("provider_account_id"),
            rs.getString("email"),
            rs.getString("encrypted_access_token"),
            rs.getString("encrypted_refresh_token"),
            rs.getInt("is_primary") == 1,
            parseInstant(rs.getString("connected_at")),
            parseInstant(rs.getString("last_sync_at"))
    );

    private static Instant parseInstant(String value) {
        return value != null ? Instant.parse(value) : null;
    }

    @Override
    public List<CalendarAccount> findAllByAdminId(Long adminId) {
        return jdbc.query(
                "SELECT * FROM calendar_accounts WHERE admin_id = ? ORDER BY connected_at",
                ROW_MAPPER, adminId);
    }

    @Override
    public Optional<CalendarAccount> findById(Long id, Long adminId) {
        List<CalendarAccount> results = jdbc.query(
                "SELECT * FROM calendar_accounts WHERE id = ? AND admin_id = ?",
                ROW_MAPPER, id, adminId);
        return results.stream().findFirst();
    }

    @Override
    public CalendarAccount save(CalendarAccount account) {
        if (account.id() == null) {
            return insert(account);
        }
        return update(account);
    }

    @Override
    public void delete(Long id, Long adminId) {
        jdbc.update("DELETE FROM calendar_accounts WHERE id = ? AND admin_id = ?", id, adminId);
    }

    @Override
    @Transactional
    public void setPrimary(Long id, Long adminId) {
        jdbc.update("UPDATE calendar_accounts SET is_primary = 0 WHERE admin_id = ?", adminId);
        jdbc.update("UPDATE calendar_accounts SET is_primary = 1 WHERE id = ? AND admin_id = ?", id, adminId);
    }

    // ON CONFLICT DO UPDATE is a true in-place upsert (SQLite 3.24+): it updates only token
    // columns while preserving id, is_primary, and connected_at. INSERT OR REPLACE would
    // do a DELETE + INSERT, changing the rowid and cascading deletion of calendar_events.
    private CalendarAccount insert(CalendarAccount account) {
        Instant connectedAt = account.connectedAt() != null ? account.connectedAt() : Instant.now();
        jdbc.update(
                "INSERT INTO calendar_accounts " +
                "(admin_id, provider, provider_account_id, email, " +
                " encrypted_access_token, encrypted_refresh_token, is_primary, connected_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (admin_id, provider, provider_account_id) DO UPDATE SET " +
                "  email = excluded.email, " +
                "  encrypted_access_token = excluded.encrypted_access_token, " +
                "  encrypted_refresh_token = excluded.encrypted_refresh_token",
                account.adminId(), account.provider(), account.providerAccountId(),
                account.email(), account.encryptedAccessToken(), account.encryptedRefreshToken(),
                account.isPrimary() ? 1 : 0, connectedAt.toString());
        // SELECT back so the caller always receives the canonical row (preserved id, is_primary, connected_at).
        return jdbc.queryForObject(
                "SELECT * FROM calendar_accounts WHERE admin_id = ? AND provider = ? AND provider_account_id = ?",
                ROW_MAPPER,
                account.adminId(), account.provider(), account.providerAccountId());
    }

    private CalendarAccount update(CalendarAccount account) {
        jdbc.update(
                "UPDATE calendar_accounts SET " +
                "email = ?, encrypted_access_token = ?, encrypted_refresh_token = ?, " +
                "is_primary = ?, last_sync_at = ? " +
                "WHERE id = ? AND admin_id = ?",
                account.email(),
                account.encryptedAccessToken(),
                account.encryptedRefreshToken(),
                account.isPrimary() ? 1 : 0,
                account.lastSyncAt() != null ? account.lastSyncAt().toString() : null,
                account.id(),
                account.adminId());
        return account;
    }
}
