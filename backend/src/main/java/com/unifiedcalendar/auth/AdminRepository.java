package com.unifiedcalendar.auth;

import java.util.Optional;

public interface AdminRepository {
    /** Looks up an admin by email for login and uniqueness checks. */
    Optional<Admin> findByEmail(String email);

    /** Resolves an admin by public slug for short links and public availability. */
    Optional<Admin> findBySlug(String slug);

    /** Loads an admin by primary key — used by session-protected endpoints after reading adminId from session. */
    Optional<Admin> findById(Long id);

    /** Creates or updates an admin record and returns the persisted entity. */
    Admin save(Admin admin);
}
