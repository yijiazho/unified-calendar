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
) {}
