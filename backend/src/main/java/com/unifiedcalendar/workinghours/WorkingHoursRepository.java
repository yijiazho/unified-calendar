package com.unifiedcalendar.workinghours;

import java.util.List;

public interface WorkingHoursRepository {
    List<WorkingHours> findAllByAdminId(Long adminId);
    void replaceAll(Long adminId, List<WorkingHours> hours);
}
