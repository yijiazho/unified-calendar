package com.unifiedcalendar.calendar;

/** Returned by token refreshers — carries the plaintext access token for immediate API use and the
 * updated account (with new encrypted tokens) for the caller to persist. */
public record TokenRefreshResult(String accessToken, CalendarAccount updatedAccount) {}
