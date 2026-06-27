package com.unifiedcalendar.calendar;

import java.time.Instant;

public record CalendarAccount(
        Long id,
        Long adminId,
        Provider provider,
        String providerAccountId,
        String email,
        String encryptedAccessToken,
        String encryptedRefreshToken,
        boolean isPrimary,
        Instant connectedAt,
        Instant lastSyncAt,
        String lastSyncError
) {}
