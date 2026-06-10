package com.unifiedcalendar.calendar;

import java.util.List;
import java.util.Optional;

public interface CalendarAccountRepository {
    List<CalendarAccount> findAllByAdminId(Long adminId);
    Optional<CalendarAccount> findById(Long id, Long adminId);
    CalendarAccount save(CalendarAccount account);
    void delete(Long id, Long adminId);
    void setPrimary(Long id, Long adminId);
}
