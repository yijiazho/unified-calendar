package com.unifiedcalendar.auth;

/** Thrown when a protected endpoint is accessed without a valid admin session. */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException() {
        super("Not authenticated");
    }
}
