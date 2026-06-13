package com.unifiedcalendar.auth;

/** Thrown when a required request field is null or blank. */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
