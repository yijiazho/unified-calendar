package com.unifiedcalendar.auth;

import java.time.Instant;

public record Admin(
        Long id,
        String email,
        String passwordHash,
        String slug,
        String timezone,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * Returns the MVP public display name defined by TASK-016. A persisted display-name field can
     * replace this derivation later without exposing the URL slug as visitor-facing copy.
     */
    public String displayName() {
        int separator = email.indexOf('@');
        return separator >= 0 ? email.substring(0, separator) : email;
    }
}
