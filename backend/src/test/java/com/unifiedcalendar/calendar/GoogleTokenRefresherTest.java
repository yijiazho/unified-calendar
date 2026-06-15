package com.unifiedcalendar.calendar;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.unifiedcalendar.config.EncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

// allow-bean-definition-overriding lets MockTransportConfig.googleHttpTransport() replace
// the production NetHttpTransport bean so no real HTTP call is made during the test.
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("GoogleTokenRefresher")
class GoogleTokenRefresherTest {

    @TestConfiguration
    static class MockTransportConfig {
        /** Intercepts all Google token-endpoint calls and returns a canned refresh response. */
        @Bean
        @Primary
        public HttpTransport googleHttpTransport() {
            return new MockHttpTransport() {
                @Override
                public LowLevelHttpRequest buildRequest(String method, String url) {
                    return new MockLowLevelHttpRequest() {
                        @Override
                        public LowLevelHttpResponse execute() throws IOException {
                            MockLowLevelHttpResponse resp = new MockLowLevelHttpResponse();
                            resp.setStatusCode(200);
                            resp.setContentType("application/json");
                            resp.setContent(
                                    "{\"access_token\":\"new-access-token\"," +
                                    "\"token_type\":\"Bearer\"," +
                                    "\"expires_in\":3600}");
                            return resp;
                        }
                    };
                }
            };
        }
    }

    // Prevent GoogleOAuthService from making HTTP calls in this test context.
    @MockBean
    @SuppressWarnings("unused")
    private GoogleOAuthService googleOAuthService;

    @Autowired
    private GoogleTokenRefresher refresher;

    @Autowired
    private CalendarAccountRepository repository;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private JdbcTemplate jdbc;

    private Long adminId;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO admins (email, password_hash, slug) VALUES (?, ?, ?)",
                "refresh-test@example.com", "hash", "refresh-test-slug");
        adminId = jdbc.queryForObject(
                "SELECT id FROM admins WHERE slug = 'refresh-test-slug'", Long.class);
    }

    @Test
    @DisplayName("refreshAccessToken returns the new plaintext token for immediate use")
    void refreshReturnsNewToken() {
        String result = refresher.refreshAccessToken(savedAccount("sub-r1"));
        assertEquals("new-access-token", result);
    }

    @Test
    @DisplayName("refreshAccessToken persists the new encrypted access token in the database")
    void refreshPersistsEncryptedToken() {
        CalendarAccount account = savedAccount("sub-r2");
        refresher.refreshAccessToken(account);

        CalendarAccount updated = repository.findById(account.id(), adminId).orElseThrow();
        assertEquals("new-access-token", encryptionService.decrypt(updated.encryptedAccessToken()));
    }

    @Test
    @DisplayName("refreshAccessToken preserves the existing encrypted refresh token")
    void refreshPreservesRefreshToken() {
        CalendarAccount account = savedAccount("sub-r3");
        refresher.refreshAccessToken(account);

        CalendarAccount updated = repository.findById(account.id(), adminId).orElseThrow();
        assertEquals("valid-refresh-token", encryptionService.decrypt(updated.encryptedRefreshToken()));
    }

    private CalendarAccount savedAccount(String sub) {
        return repository.save(new CalendarAccount(
                null, adminId, "GOOGLE", sub, "user@gmail.com",
                encryptionService.encrypt("old-access-token"),
                encryptionService.encrypt("valid-refresh-token"),
                false, Instant.now(), null));
    }
}
