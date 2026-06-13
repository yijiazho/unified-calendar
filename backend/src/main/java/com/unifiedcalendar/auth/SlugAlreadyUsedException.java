package com.unifiedcalendar.auth;

/** Thrown when signup is attempted with a slug that is already claimed by another admin. */
public class SlugAlreadyUsedException extends RuntimeException {
    public SlugAlreadyUsedException(String slug) {
        super("Slug already in use: " + slug);
    }
}
