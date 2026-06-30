package com.unifiedcalendar.availability;

/** Response body for GET /s/{slug} — public admin profile. */
public record AdminPublicInfoResponse(String slug, String name, String timezone) {}
