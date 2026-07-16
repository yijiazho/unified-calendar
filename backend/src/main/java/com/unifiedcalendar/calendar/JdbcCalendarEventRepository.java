package com.unifiedcalendar.calendar;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCalendarEventRepository implements CalendarEventRepository {

    private final JdbcTemplate jdbc;

    public JdbcCalendarEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<CalendarEvent> ROW_MAPPER = (rs, rowNum) -> new CalendarEvent(
            rs.getLong("id"),
            rs.getLong("admin_id"),
            rs.getLong("calendar_account_id"),
            Provider.valueOf(rs.getString("provider")),
            rs.getString("provider_event_id"),
            rs.getString("title"),
            Instant.parse(rs.getString("start_time_utc")),
            Instant.parse(rs.getString("end_time_utc")),
            rs.getInt("is_booking_event") == 1,
            parseInstant(rs.getString("provider_updated_at")),
            parseInstant(rs.getString("last_synced_at"))
    );

    private static Instant parseInstant(String value) {
        return value != null ? Instant.parse(value) : null;
    }

    /**
     * Inserts or updates a provider event. {@code is_booking_event} is deliberately excluded from the
     * ON CONFLICT UPDATE so that booking-created rows (written by Phase 2 with the flag set to true) survive
     * subsequent sync cycles. Callers must always pass {@code isBookingEvent = false} for sync-sourced events.
     * If a sync cycle runs before Phase 2 writes the booking row, the flag will be stored as false; Phase 2
     * must therefore write its row atomically with event creation to prevent this race.
     */
    @Override
    public void upsert(CalendarEvent event) {
        jdbc.update(
            "INSERT INTO calendar_events " +
            "(admin_id, calendar_account_id, provider, provider_event_id, title, " +
            " start_time_utc, end_time_utc, is_booking_event, provider_updated_at, last_synced_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
            "ON CONFLICT (calendar_account_id, provider_event_id) DO UPDATE SET " +
            "  title = excluded.title, " +
            "  start_time_utc = excluded.start_time_utc, " +
            "  end_time_utc = excluded.end_time_utc, " +
            "  provider_updated_at = excluded.provider_updated_at, " +
            "  last_synced_at = excluded.last_synced_at",
            event.adminId(), event.calendarAccountId(), event.provider().name(), event.providerEventId(),
            event.title(),
            event.startTimeUtc().toString(), event.endTimeUtc().toString(),
            event.isBookingEvent() ? 1 : 0,
            event.providerUpdatedAt() != null ? event.providerUpdatedAt().toString() : null,
            Instant.now().toString()
        );
    }

    @Override
    public void deleteByCalendarAccountIdAndProviderEventIdNotIn(Long calendarAccountId, List<String> seenProviderEventIds) {
        if (seenProviderEventIds.isEmpty()) {
            jdbc.update("DELETE FROM calendar_events WHERE calendar_account_id = ?", calendarAccountId);
            return;
        }
        // NOT IN with a large list hits SQLite's SQLITE_MAX_VARIABLE_NUMBER (999 by default).
        // Instead: fetch current IDs, diff client-side, then delete stale ones with IN — chunked at 500
        // to leave room for the calendarAccountId bind parameter within the limit.
        Set<String> seenSet = new HashSet<>(seenProviderEventIds);
        List<String> toDelete = jdbc.queryForList(
            "SELECT provider_event_id FROM calendar_events WHERE calendar_account_id = ?",
            String.class, calendarAccountId)
            .stream().filter(id -> !seenSet.contains(id)).toList();

        for (int i = 0; i < toDelete.size(); i += 500) {
            List<String> chunk = toDelete.subList(i, Math.min(i + 500, toDelete.size()));
            String placeholders = chunk.stream().map(id -> "?").collect(Collectors.joining(", "));
            List<Object> params = new ArrayList<>();
            params.add(calendarAccountId);
            params.addAll(chunk);
            jdbc.update(
                "DELETE FROM calendar_events WHERE calendar_account_id = ? AND provider_event_id IN (" + placeholders + ")",
                params.toArray()
            );
        }
    }

    @Override
    public List<CalendarEvent> findByAdminIdAndTimeRange(Long adminId, Instant from, Instant to) {
        // Returns events that overlap with [from, to]: started before `to` and ended after `from`.
        return jdbc.query(
            "SELECT * FROM calendar_events " +
            "WHERE admin_id = ? AND start_time_utc < ? AND end_time_utc > ? " +
            "ORDER BY start_time_utc",
            ROW_MAPPER, adminId, to.toString(), from.toString()
        );
    }

    @Override
    public List<CalendarEventResponse> findWithEmailByAdminIdAndTimeRange(Long adminId, Instant start, Instant end) {
        // JOIN with calendar_accounts to include the email that owns each event.
        return jdbc.query(
            "SELECT ce.id, ce.title, ce.start_time_utc, ce.end_time_utc, ce.provider, " +
            "  ce.calendar_account_id, ca.email AS calendar_email, ce.is_booking_event " +
            "FROM calendar_events ce " +
            "JOIN calendar_accounts ca ON ca.id = ce.calendar_account_id " +
            "WHERE ce.admin_id = ? AND ce.start_time_utc < ? AND ce.end_time_utc > ? " +
            "ORDER BY ce.start_time_utc ASC",
            (rs, rowNum) -> new CalendarEventResponse(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("start_time_utc"),
                rs.getString("end_time_utc"),
                rs.getString("provider"),
                rs.getLong("calendar_account_id"),
                rs.getString("calendar_email"),
                rs.getInt("is_booking_event") == 1
            ),
            adminId, end.toString(), start.toString()
        );
    }

    /** Inserts a booking-sourced event with is_booking_event=true and returns the generated primary key. */
    @Override
    public Long insertBookingEvent(CalendarEvent event) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement(
                "INSERT INTO calendar_events " +
                "(admin_id, calendar_account_id, provider, provider_event_id, title, " +
                " start_time_utc, end_time_utc, is_booking_event, provider_updated_at, last_synced_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, ?)",
                new String[]{"id"});
            ps.setLong(1, event.adminId());
            ps.setLong(2, event.calendarAccountId());
            ps.setString(3, event.provider().name());
            ps.setString(4, event.providerEventId());
            ps.setString(5, event.title());
            ps.setString(6, event.startTimeUtc().toString());
            ps.setString(7, event.endTimeUtc().toString());
            ps.setString(8, event.providerUpdatedAt() != null ? event.providerUpdatedAt().toString() : null);
            ps.setString(9, Instant.now().toString());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new RuntimeException("calendar_events insert did not return a generated key");
        }
        return key.longValue();
    }
}
