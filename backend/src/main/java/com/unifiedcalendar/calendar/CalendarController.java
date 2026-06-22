package com.unifiedcalendar.calendar;

import com.unifiedcalendar.auth.SessionUtils;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/calendar")
public class CalendarController {

    private static final Logger log = LoggerFactory.getLogger(CalendarController.class);

    private final GoogleOAuthService googleOAuthService;
    private final OutlookOAuthService outlookOAuthService;
    private final CalendarAccountRepository repository;
    private final String frontendBaseUrl;

    public CalendarController(
            GoogleOAuthService googleOAuthService,
            OutlookOAuthService outlookOAuthService,
            CalendarAccountRepository repository,
            @Value("${app.base-url}") String frontendBaseUrl) {
        this.googleOAuthService = googleOAuthService;
        this.outlookOAuthService = outlookOAuthService;
        this.repository = repository;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /** Initiates the Google OAuth2 flow by redirecting the logged-in admin to Google's consent screen. */
    @GetMapping("/google/connect")
    public void googleConnect(HttpSession session, HttpServletResponse response) throws IOException {
        Long adminId = SessionUtils.requireAdminId(session);
        String authUrl = googleOAuthService.buildAuthorizationUrl(adminId);
        response.sendRedirect(authUrl);
    }

    /** Handles Google's redirect after consent; state parameter is validated to prevent CSRF. */
    @GetMapping("/google/callback")
    public void googleCallback(
            @RequestParam String code,
            @RequestParam String state,
            HttpServletResponse response) throws IOException {
        try {
            googleOAuthService.handleCallback(code, state);
            response.sendRedirect(frontendBaseUrl + "/settings/calendars");
        } catch (IllegalArgumentException e) {
            // Invalid or tampered state — likely a CSRF attempt.
            log.warn("Google OAuth callback rejected — invalid state parameter: {}", e.getMessage());
            response.sendRedirect(frontendBaseUrl + "/settings/calendars?error=google_oauth_failed");
        } catch (Exception e) {
            log.error("Google OAuth callback failed", e);
            response.sendRedirect(frontendBaseUrl + "/settings/calendars?error=google_oauth_failed");
        }
    }

    /** Initiates the Microsoft OAuth2 flow by redirecting the logged-in admin to Entra ID's consent screen. */
    @GetMapping("/outlook/connect")
    public void outlookConnect(HttpSession session, HttpServletResponse response) throws IOException {
        Long adminId = SessionUtils.requireAdminId(session);
        String authUrl = outlookOAuthService.buildAuthorizationUrl(adminId);
        response.sendRedirect(authUrl);
    }

    /** Handles Microsoft's redirect after consent; state parameter is validated to prevent CSRF. */
    @GetMapping("/outlook/callback")
    public void outlookCallback(
            @RequestParam String code,
            @RequestParam String state,
            HttpServletResponse response) throws IOException {
        try {
            outlookOAuthService.handleCallback(code, state);
            response.sendRedirect(frontendBaseUrl + "/settings/calendars");
        } catch (IllegalArgumentException e) {
            log.warn("Outlook OAuth callback rejected — invalid state parameter: {}", e.getMessage());
            response.sendRedirect(frontendBaseUrl + "/settings/calendars?error=outlook_oauth_failed");
        } catch (IllegalStateException e) {
            log.error("Outlook OAuth callback failed — token issue: {}", e.getMessage());
            response.sendRedirect(frontendBaseUrl + "/settings/calendars?error=outlook_oauth_failed");
        } catch (Exception e) {
            log.error("Outlook OAuth callback failed", e);
            response.sendRedirect(frontendBaseUrl + "/settings/calendars?error=outlook_oauth_failed");
        }
    }

    /** Returns all Google and Outlook calendar accounts connected by the authenticated admin. */
    @GetMapping("/accounts")
    public List<CalendarAccountResponse> listAccounts(HttpSession session) {
        Long adminId = SessionUtils.requireAdminId(session);
        return repository.findAllByAdminId(adminId).stream()
                .map(CalendarAccountResponse::from)
                .toList();
    }

    /** Removes a connected calendar account and its cached events (cascades via FK). */
    @DeleteMapping("/accounts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@PathVariable Long id, HttpSession session) {
        Long adminId = SessionUtils.requireAdminId(session);
        repository.delete(id, adminId);
    }

    /** Sets exactly one account as the admin's primary calendar; the previous primary is cleared atomically. */
    @PutMapping("/primary")
    public List<CalendarAccountResponse> setPrimary(
            @RequestBody SetPrimaryRequest request,
            HttpSession session) {
        Long adminId = SessionUtils.requireAdminId(session);
        // Note: findById and setPrimary are separate transactions.
        // Acceptable for MVP single-session use; consolidate into a single transactional
        // service method if concurrent admin sessions are introduced in Phase 2.
        repository.findById(request.accountId(), adminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Calendar account not found"));
        repository.setPrimary(request.accountId(), adminId);
        return repository.findAllByAdminId(adminId).stream()
                .map(CalendarAccountResponse::from)
                .toList();
    }
}
