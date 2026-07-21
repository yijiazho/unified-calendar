package com.unifiedcalendar.config;

import com.unifiedcalendar.auth.AuthenticationException;
import com.unifiedcalendar.auth.EmailAlreadyUsedException;
import com.unifiedcalendar.auth.InvalidSlugException;
import com.unifiedcalendar.auth.SlugAlreadyUsedException;
import com.unifiedcalendar.auth.UnauthorizedException;
import com.unifiedcalendar.auth.ValidationException;
import com.unifiedcalendar.booking.CancellationConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleValidation(ValidationException ex) {
        return Map.of("error", ex.getMessage());
    }

    /** Aggregates field-level @Valid failures into a single readable error message. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleValidationErrors(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + " " + e.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        return Map.of("error", message);
    }

    /** Spring 6.2 throws this for @Valid on records/method parameters (not MethodArgumentNotValidException). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        // Bean results (from @Valid @RequestBody) carry field names; prefer those.
        for (var beanResult : ex.getBeanResults()) {
            var fieldErrors = beanResult.getFieldErrors();
            if (!fieldErrors.isEmpty()) {
                var fe = fieldErrors.get(0);
                return Map.of("error", fe.getField() + " " + fe.getDefaultMessage());
            }
        }
        String message = ex.getAllErrors().stream()
                .findFirst()
                .map(e -> e.getDefaultMessage())
                .orElse("Validation failed");
        return Map.of("error", message != null ? message : "Validation failed");
    }

    /** Propagates ResponseStatusException status codes so callers receive the intended HTTP status. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        String reason = ex.getReason();
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("error", reason != null ? reason : "Request failed"));
    }

    /** Returns stable machine-readable codes for cancellation conflicts. */
    @ExceptionHandler(CancellationConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleCancellationConflict(CancellationConflictException ex) {
        return Map.of("error", ex.getMessage(), "code", ex.code().name());
    }

    /** Returns 404 for unmapped paths; without this, the Exception catch-all would return 500. */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNoHandlerFound(Exception ex) {
        return Map.of("error", "Not found");
    }

    /** Spring 6.2+ throws this before the controller runs when @RequestBody resolves to null or is malformed. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleNotReadable(HttpMessageNotReadableException ex) {
        return Map.of("error", "Request body must not be null");
    }

    /** Thrown before the controller runs when a required @RequestParam is absent. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleMissingParam(MissingServletRequestParameterException ex) {
        return Map.of("error", "Required parameter '" + ex.getParameterName() + "' is missing");
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

    /** Catches any unhandled exception and returns a generic 500 to avoid leaking stack traces. */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return Map.of("error", "Internal server error");
    }
}
