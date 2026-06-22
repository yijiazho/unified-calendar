package com.unifiedcalendar.workinghours;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
class JdbcWorkingHoursRepository implements WorkingHoursRepository {

    private final JdbcTemplate jdbc;

    JdbcWorkingHoursRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<WorkingHours> ROW_MAPPER = (rs, rowNum) -> new WorkingHours(
            rs.getLong("id"),
            rs.getLong("admin_id"),
            rs.getInt("day_of_week"),
            rs.getString("start_time"),
            rs.getString("end_time")
    );

    /** Loads all working-hours windows for an admin ordered by day. */
    @Override
    public List<WorkingHours> findAllByAdminId(Long adminId) {
        return jdbc.query(
                "SELECT id, admin_id, day_of_week, start_time, end_time FROM working_hours WHERE admin_id = ? ORDER BY day_of_week",
                ROW_MAPPER, adminId);
    }

    /** Deletes then re-inserts all rows in one transaction so no partial state is visible. */
    @Override
    @Transactional
    public void replaceAll(Long adminId, List<WorkingHours> hours) {
        jdbc.update("DELETE FROM working_hours WHERE admin_id = ?", adminId);
        for (WorkingHours wh : hours) {
            jdbc.update(
                    "INSERT INTO working_hours (admin_id, day_of_week, start_time, end_time) VALUES (?, ?, ?, ?)",
                    adminId, wh.dayOfWeek(), wh.startTime(), wh.endTime());
        }
    }
}
