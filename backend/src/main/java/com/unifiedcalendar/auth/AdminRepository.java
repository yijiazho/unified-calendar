package com.unifiedcalendar.auth;

import java.util.Optional;

public interface AdminRepository {
    /** Looks up an admin by email for login and uniqueness checks. */
    Optional<Admin> findByEmail(String email);

    /** Resolves an admin by public slug for short links and public availability. */
    Optional<Admin> findBySlug(String slug);

    /** Creates or updates an admin record and returns the persisted entity. */
    Admin save(Admin admin);
}
