package com.unifiedcalendar.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> signup(@RequestBody SignupRequest body) {
        Admin admin = authService.signup(body.email(), body.password(), body.slug(), body.timezone());
        return adminView(admin);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest body, HttpServletRequest request) {
        Admin admin = authService.login(body.email(), body.password());
        // Ensure a session exists, then rotate its ID before binding adminId. This prevents
        // session fixation: a pre-login session ID planted by an attacker becomes invalid.
        request.getSession(true);
        request.changeSessionId();
        request.getSession().setAttribute("adminId", admin.id());
        return adminView(admin);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpSession session) {
        session.invalidate();
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpSession session) {
        Long adminId = SessionUtils.requireAdminId(session);
        Admin admin = authService.getById(adminId);
        return adminView(admin);
    }

    private static Map<String, Object> adminView(Admin admin) {
        return Map.of(
                "id", admin.id(),
                "email", admin.email(),
                "slug", admin.slug(),
                "timezone", admin.timezone()
        );
    }

    record SignupRequest(String email, String password, String slug, String timezone) {}
    record LoginRequest(String email, String password) {}
}

