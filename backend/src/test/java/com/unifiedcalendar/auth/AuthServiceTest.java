package com.unifiedcalendar.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuthService")
class AuthServiceTest {

    private InMemoryAdminRepository repo;
    private AuthService service;

    @BeforeEach
    void setUp() {
        repo = new InMemoryAdminRepository();
        service = new AuthService(repo, new BCryptPasswordEncoder(4)); // low cost for tests
    }

    @Test
    @DisplayName("signup creates admin with BCrypt-hashed password")
    void signupHashesPassword() {
        Admin admin = service.signup("a@example.com", "secret", "alice", "UTC");

        assertNotNull(admin.id());
        assertEquals("a@example.com", admin.email());
        assertEquals("alice", admin.slug());
        assertNotEquals("secret", admin.passwordHash(), "password must not be stored in plaintext");
        assertTrue(admin.passwordHash().startsWith("$2"), "must be BCrypt hash");
    }

    @Test
    @DisplayName("signup with duplicate email throws EmailAlreadyUsedException")
    void signupDuplicateEmailThrows() {
        service.signup("dup@example.com", "pass", "first-slug", "UTC");

        assertThrows(EmailAlreadyUsedException.class,
                () -> service.signup("dup@example.com", "other", "second-slug", "UTC"));
    }

    @Test
    @DisplayName("signup with duplicate slug throws SlugAlreadyUsedException")
    void signupDuplicateSlugThrows() {
        service.signup("first@example.com", "pass", "taken-slug", "UTC");

        assertThrows(SlugAlreadyUsedException.class,
                () -> service.signup("second@example.com", "pass", "taken-slug", "UTC"));
    }

    @Test
    @DisplayName("signup with invalid slug throws InvalidSlugException")
    void signupInvalidSlugThrows() {
        assertThrows(InvalidSlugException.class,
                () -> service.signup("b@example.com", "pass", "Bad Slug!", "UTC"));
    }

    @Test
    @DisplayName("signup accepts valid slug characters (lowercase, digits, hyphens)")
    void signupValidSlugAccepted() {
        assertDoesNotThrow(() -> service.signup("c@example.com", "pass", "my-slug-01", "UTC"));
    }

    @Test
    @DisplayName("login returns admin on correct credentials")
    void loginSuccess() {
        service.signup("login@example.com", "mypassword", "login-slug", "America/New_York");
        Admin admin = service.login("login@example.com", "mypassword");

        assertEquals("login@example.com", admin.email());
    }

    @Test
    @DisplayName("login throws AuthenticationException for unknown email")
    void loginUnknownEmailThrows() {
        assertThrows(AuthenticationException.class,
                () -> service.login("nobody@example.com", "pass"));
    }

    @Test
    @DisplayName("login throws AuthenticationException for wrong password")
    void loginWrongPasswordThrows() {
        service.signup("wp@example.com", "correct", "wp-slug", "UTC");

        assertThrows(AuthenticationException.class,
                () -> service.login("wp@example.com", "wrong"));
    }

    // ─── minimal in-memory stub ─────────────────────────────────────────────────

    static class InMemoryAdminRepository implements AdminRepository {
        private long nextId = 1;
        private final java.util.Map<Long, Admin> store = new java.util.LinkedHashMap<>();

        @Override
        public Optional<Admin> findByEmail(String email) {
            return store.values().stream().filter(a -> a.email().equals(email)).findFirst();
        }

        @Override
        public Optional<Admin> findBySlug(String slug) {
            return store.values().stream().filter(a -> a.slug().equals(slug)).findFirst();
        }

        @Override
        public Optional<Admin> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Admin save(Admin admin) {
            long id = nextId++;
            Admin persisted = new Admin(id, admin.email(), admin.passwordHash(),
                    admin.slug(), admin.timezone(), Instant.now(), Instant.now());
            store.put(id, persisted);
            return persisted;
        }
    }
}
