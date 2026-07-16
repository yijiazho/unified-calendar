package com.unifiedcalendar.booking;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class JdbcSlotReservationRepository implements SlotReservationRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcSlotReservationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public SlotReservation reserve(Long adminId, Instant slotStart, Instant slotEnd) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO slot_reservations (admin_id, slot_start, slot_end) VALUES (?, ?, ?)",
                        new String[]{"id"});
                ps.setLong(1, adminId);
                ps.setString(2, slotStart.toString());
                ps.setString(3, slotEnd.toString());
                return ps;
            }, keyHolder);
        } catch (DataIntegrityViolationException e) {
            // Unique constraint violation means slot is already reserved
            throw e;
        }

        Long id = keyHolder.getKey().longValue();
        return new SlotReservation(id, adminId, slotStart, slotEnd, Instant.now());
    }

    @Override
    public Optional<SlotReservation> findBySlot(Long adminId, Instant slotStart, Instant slotEnd) {
        return jdbcTemplate.query(
                "SELECT id, admin_id, slot_start, slot_end, reserved_at FROM slot_reservations WHERE admin_id = ? AND slot_start = ? AND slot_end = ?",
                new Object[]{adminId, slotStart.toString(), slotEnd.toString()},
                rs -> {
                    if (rs.next()) {
                        return Optional.of(new SlotReservation(
                                rs.getLong("id"),
                                rs.getLong("admin_id"),
                                Instant.parse(rs.getString("slot_start")),
                                Instant.parse(rs.getString("slot_end")),
                                Instant.parse(rs.getString("reserved_at"))
                        ));
                    }
                    return Optional.empty();
                }
        );
    }

    @Override
    public void delete(Long reservationId) {
        jdbcTemplate.update("DELETE FROM slot_reservations WHERE id = ?", reservationId);
    }
}
