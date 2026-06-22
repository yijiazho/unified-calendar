package com.unifiedcalendar.calendar;

import java.util.List;
import java.util.Optional;

public interface CalendarAccountRepository {
    /** Returns every calendar account across all admins — used by the background sync scheduler. */
    List<CalendarAccount> findAll();

    /** Lists all calendar accounts owned by the given admin (admin-scoped). */
    List<CalendarAccount> findAllByAdminId(Long adminId);

    /** Loads a single calendar account by id, constrained to the owning admin. */
    Optional<CalendarAccount> findById(Long id, Long adminId);

    /** Creates or updates a calendar account and returns the persisted record. */
    CalendarAccount save(CalendarAccount account);

    /** Deletes a calendar account by id, constrained to the owning admin. */
    void delete(Long id, Long adminId);

    /** Marks the given account as the admin's primary calendar (and unmarks any previous primary). */
    void setPrimary(Long id, Long adminId);
}
