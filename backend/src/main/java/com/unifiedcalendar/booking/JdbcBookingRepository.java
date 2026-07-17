package com.unifiedcalendar.booking;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcBookingRepository implements BookingRepository {

    private final JdbcTemplate jdbc;

    public JdbcBookingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Booking> ROW_MAPPER = (rs, rowNum) -> {
        Number calendarEventId = (Number) rs.getObject("calendar_event_id");
        return new Booking(
                rs.getLong("id"),
                rs.getLong("admin_id"),
                calendarEventId != null ? calendarEventId.longValue() : null,
                rs.getString("visitor_name"),
                rs.getString("visitor_email"),
                rs.getString("visitor_phone"),
                rs.getString("notes"),
                rs.getString("status"),
                rs.getString("cancel_token"),
                rs.getString("reschedule_token"),
                Instant.parse(rs.getString("created_at"))
        );
    };

    /** Inserts a new booking and returns the persisted record with the generated id. */
    @Override
    public Booking save(Booking booking) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement(
                "INSERT INTO bookings " +
                "(admin_id, calendar_event_id, visitor_name, visitor_email, visitor_phone, " +
                " notes, status, cancel_token, reschedule_token) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                new String[]{"id"});
            ps.setLong(1, booking.adminId());
            ps.setObject(2, booking.calendarEventId());
            ps.setString(3, booking.visitorName());
            ps.setString(4, booking.visitorEmail());
            ps.setString(5, booking.visitorPhone());
            ps.setString(6, booking.notes());
            ps.setString(7, booking.status());
            ps.setString(8, booking.cancelToken());
            ps.setString(9, booking.rescheduleToken());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new RuntimeException("bookings insert did not return a generated key");
        }
        // Fetch the persisted record to get the actual DB-generated created_at timestamp
        Booking persisted = findByIdForRefresh(key.longValue());
        if (persisted == null) {
            throw new RuntimeException("Failed to retrieve inserted booking by id");
        }
        return persisted;
    }

    /** Internal method to fetch a booking by id (used after insert to get DB-generated timestamps). */
    private Booking findByIdForRefresh(Long id) {
        List<Booking> results = jdbc.query(
                "SELECT * FROM bookings WHERE id = ?", ROW_MAPPER, id);
        return results.stream().findFirst().orElse(null);
    }

    @Override
    public Optional<Booking> findByCancelToken(String token) {
        List<Booking> results = jdbc.query(
                "SELECT * FROM bookings WHERE cancel_token = ?", ROW_MAPPER, token);
        return results.stream().findFirst();
    }

    @Override
    public Optional<Booking> findByRescheduleToken(String token) {
        List<Booking> results = jdbc.query(
                "SELECT * FROM bookings WHERE reschedule_token = ?", ROW_MAPPER, token);
        return results.stream().findFirst();
    }

    @Override
    public void updateStatus(Long id, String status) {
        jdbc.update("UPDATE bookings SET status = ? WHERE id = ?", status, id);
    }

    @Override
    public boolean updateStatusIfCurrent(Long id, String expectedStatus, String newStatus) {
        return jdbc.update(
                "UPDATE bookings SET status = ? WHERE id = ? AND status = ?",
                newStatus, id, expectedStatus) == 1;
    }
}
