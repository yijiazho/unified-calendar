package com.unifiedcalendar.calendar;

import com.unifiedcalendar.auth.SessionUtils;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/calendar")
public class CalendarController {

    private static final Logger log = LoggerFactory.getLogger(CalendarController.class);

    private final GoogleOAuthService googleOAuthService;
    private final String frontendBaseUrl;

    public CalendarController(
            GoogleOAuthService googleOAuthService,
            @Value("${app.base-url}") String frontendBaseUrl) {
        this.googleOAuthService = googleOAuthService;
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
}
