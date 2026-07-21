package com.unifiedcalendar.booking;

/** A cancellation conflict with a stable code suitable for API clients. */
public class CancellationConflictException extends RuntimeException {

    public enum Code {
        ALREADY_CANCELLED,
        ALREADY_RESCHEDULED
    }

    private final Code code;

    public CancellationConflictException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
