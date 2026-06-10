package com.unifiedcalendar.workinghours;

import java.util.List;

public interface WorkingHoursRepository {
    /** Loads all working-hours windows for an admin used by availability calculations. */
    List<WorkingHours> findAllByAdminId(Long adminId);

    /** Atomically replaces the admin's working-hours windows with the provided set. */
    void replaceAll(Long adminId, List<WorkingHours> hours);
}
