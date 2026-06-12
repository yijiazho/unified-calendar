package com.unifiedcalendar.auth;

/** Thrown when signup is attempted with an email that already exists in the admins table. */
public class EmailAlreadyUsedException extends RuntimeException {
    public EmailAlreadyUsedException(String email) {
        super("Email already in use: " + email);
    }
}
