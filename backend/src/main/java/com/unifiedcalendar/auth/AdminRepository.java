package com.unifiedcalendar.auth;

import java.util.Optional;

public interface AdminRepository {
    Optional<Admin> findByEmail(String email);
    Optional<Admin> findBySlug(String slug);
    Admin save(Admin admin);
}
