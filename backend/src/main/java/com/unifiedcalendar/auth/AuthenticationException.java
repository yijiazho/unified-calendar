package com.unifiedcalendar.auth;

/** Thrown when login credentials are invalid (unknown email or wrong password). */
public class AuthenticationException extends RuntimeException {
    public AuthenticationException() {
        super("Invalid credentials");
    }
}
