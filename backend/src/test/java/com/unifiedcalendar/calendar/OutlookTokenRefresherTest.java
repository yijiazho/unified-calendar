package com.unifiedcalendar.calendar;

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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// allow-bean-definition-overriding lets MockRestClientConfig override the production microsoftRestClient bean.
@SpringBootTest(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "microsoft.tenant-id=test-tenant"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("OutlookTokenRefresher")
class OutlookTokenRefresherTest {

    @TestConfiguration
    static class MockRestClientConfig {
        // Exposed as a static field so individual tests can set expectations on it.
        // DirtiesContext recreates the Spring context for each test, so this field is reassigned
        // and a fresh MockRestServiceServer is available for every test method.
        static MockRestServiceServer mockServer;

        /** Provides a RestClient pre-wired to MockRestServiceServer so no real HTTP calls are made. */
        @Bean(name = "microsoftRestClient")
        @Primary
        public RestClient microsoftRestClient() {
            RestClient.Builder builder = RestClient.builder();
            mockServer = MockRestServiceServer.bindTo(builder).build();
            return builder.build();
        }
    }

    // Prevent GoogleOAuthService from making HTTP calls (GoogleIdTokenVerifier fetches keys on init).
    @MockBean
    @SuppressWarnings("unused")
    private GoogleOAuthService googleOAuthService;

    @Autowired
    private OutlookTokenRefresher refresher;

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
                "outlook-refresh-test@example.com", "hash", "outlook-refresh-test-slug");
        adminId = jdbc.queryForObject(
                "SELECT id FROM admins WHERE slug = 'outlook-refresh-test-slug'", Long.class);
    }

    @Test
    @DisplayName("refreshAccessToken returns the new plaintext token for immediate use")
    void refreshReturnsNewToken() {
        MockRestClientConfig.mockServer
                .expect(requestTo(org.hamcrest.Matchers.containsString("/oauth2/v2.0/token")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"access_token\":\"new-access-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));

        TokenRefreshResult result = refresher.refreshAccessToken(savedAccount("oid-r1"));
        assertEquals("new-access-token", result.accessToken());
    }

    @Test
    @DisplayName("refreshAccessToken returns account with new encrypted access token")
    void refreshReturnsEncryptedToken() {
        MockRestClientConfig.mockServer
                .expect(requestTo(org.hamcrest.Matchers.containsString("/oauth2/v2.0/token")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"access_token\":\"new-access-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));

        CalendarAccount account = savedAccount("oid-r2");
        TokenRefreshResult result = refresher.refreshAccessToken(account);

        assertEquals("new-access-token",
                encryptionService.decrypt(result.updatedAccount().encryptedAccessToken()));
    }

    @Test
    @DisplayName("refreshAccessToken preserves the existing encrypted refresh token in the returned account")
    void refreshPreservesRefreshToken() {
        MockRestClientConfig.mockServer
                .expect(requestTo(org.hamcrest.Matchers.containsString("/oauth2/v2.0/token")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"access_token\":\"new-access-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));

        CalendarAccount account = savedAccount("oid-r3");
        TokenRefreshResult result = refresher.refreshAccessToken(account);

        assertEquals("valid-refresh-token",
                encryptionService.decrypt(result.updatedAccount().encryptedRefreshToken()));
    }

    @Test
    @DisplayName("refreshAccessToken returns account with new refresh token when Microsoft returns one (rolling token policy)")
    void refreshReturnsNewRefreshToken() {
        MockRestClientConfig.mockServer
                .expect(requestTo(org.hamcrest.Matchers.containsString("/oauth2/v2.0/token")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"access_token\":\"new-access-token\",\"refresh_token\":\"new-refresh-token\"," +
                        "\"token_type\":\"Bearer\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));

        CalendarAccount account = savedAccount("oid-r4");
        TokenRefreshResult result = refresher.refreshAccessToken(account);

        assertEquals("new-refresh-token",
                encryptionService.decrypt(result.updatedAccount().encryptedRefreshToken()),
                "new refresh token from Microsoft must be present in updated account to avoid invalid_grant on next refresh");
    }

    private CalendarAccount savedAccount(String oid) {
        return repository.save(new CalendarAccount(
                null, adminId, Provider.OUTLOOK, oid, "user@outlook.com",
                encryptionService.encrypt("old-access-token"),
                encryptionService.encrypt("valid-refresh-token"),
                false, Instant.now(), null, null));
    }
}
