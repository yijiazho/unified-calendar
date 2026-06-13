package com.unifiedcalendar.config;

import com.unifiedcalendar.auth.AuthenticationException;
import com.unifiedcalendar.auth.EmailAlreadyUsedException;
import com.unifiedcalendar.auth.InvalidSlugException;
import com.unifiedcalendar.auth.SlugAlreadyUsedException;
import com.unifiedcalendar.auth.UnauthorizedException;
import com.unifiedcalendar.auth.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleValidation(ValidationException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(EmailAlreadyUsedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleEmailAlreadyUsed(EmailAlreadyUsedException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(InvalidSlugException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleInvalidSlug(InvalidSlugException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(SlugAlreadyUsedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleSlugAlreadyUsed(SlugAlreadyUsedException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> handleAuthentication(AuthenticationException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> handleUnauthorized(UnauthorizedException ex) {
        return Map.of("error", ex.getMessage());
    }
}
