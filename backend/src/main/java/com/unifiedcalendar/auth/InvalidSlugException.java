package com.unifiedcalendar.auth;

/** Thrown when the requested slug does not match the URL-safe pattern ^[a-z0-9-]+$. */
public class InvalidSlugException extends RuntimeException {
    public InvalidSlugException(String slug) {
        super("Slug must match ^[a-z0-9-]+$: " + slug);
    }
}
