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
@RequestMapping("/calendar/outlook")
public class OutlookOAuthController {

    private static final Logger log = LoggerFactory.getLogger(OutlookOAuthController.class);

    private final OutlookOAuthService outlookOAuthService;
    private final String frontendBaseUrl;

    public OutlookOAuthController(
            OutlookOAuthService outlookOAuthService,
            @Value("${app.base-url}") String frontendBaseUrl) {
        this.outlookOAuthService = outlookOAuthService;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /** Initiates the Microsoft OAuth2 flow by redirecting the logged-in admin to Entra ID's consent screen. */
    @GetMapping("/connect")
    public void connect(HttpSession session, HttpServletResponse response) throws IOException {
        Long adminId = SessionUtils.requireAdminId(session);
        response.sendRedirect(outlookOAuthService.buildAuthorizationUrl(adminId));
    }

    /** Handles Microsoft's redirect after consent; state parameter is validated to prevent CSRF. */
    @GetMapping("/callback")
    public void callback(
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
}
