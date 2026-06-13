package com.unifiedcalendar.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9-]+$");

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AdminRepository adminRepository, PasswordEncoder passwordEncoder) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Creates a new admin account; throws if the email/slug is taken or the slug format is invalid. */
    public Admin signup(String email, String password, String slug, String timezone) {
        requireNonBlank(email, "email");
        requireNonBlank(password, "password");
        requireNonBlank(slug, "slug");
        requireNonBlank(timezone, "timezone");
        if (adminRepository.findByEmail(email).isPresent()) {
            throw new EmailAlreadyUsedException(email);
        }
        if (!SLUG_PATTERN.matcher(slug).matches()) {
            throw new InvalidSlugException(slug);
        }
        if (adminRepository.findBySlug(slug).isPresent()) {
            throw new SlugAlreadyUsedException(slug);
        }
        String hash = passwordEncoder.encode(password);
        Admin admin = new Admin(null, email, hash, slug, timezone, null, null);
        return adminRepository.save(admin);
    }

    /** Loads an admin by id for session-protected endpoints; throws UnauthorizedException if not found. */
    public Admin getById(Long id) {
        return adminRepository.findById(id)
                .orElseThrow(UnauthorizedException::new);
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(field + " is required");
        }
    }

    /** Verifies credentials and returns the matching admin; throws AuthenticationException on any mismatch. */
    public Admin login(String email, String password) {
        requireNonBlank(email, "email");
        requireNonBlank(password, "password");
        Admin admin = adminRepository.findByEmail(email)
                .orElseThrow(AuthenticationException::new);
        if (!passwordEncoder.matches(password, admin.passwordHash())) {
            throw new AuthenticationException();
        }
        return admin;
    }
}

