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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SpringBootTest(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "microsoft.tenant-id=test-tenant"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("OutlookOAuthService.handleCallback")
class OutlookOAuthServiceCallbackTest {

    @TestConfiguration
    static class MockRestClientConfig {
        static MockRestServiceServer mockServer;

        @Bean(name = "microsoftRestClient")
        @Primary
        public RestClient microsoftRestClient() {
            RestClient.Builder builder = RestClient.builder();
            mockServer = MockRestServiceServer.bindTo(builder).build();
            return builder.build();
        }
    }

    // Prevent GoogleIdTokenVerifier from making network calls during context init.
    @MockBean
    @SuppressWarnings("unused")
    private GoogleOAuthService googleOAuthService;

    @Autowired
    private OutlookOAuthService service;

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
                "outlook-callback-test@example.com", "hash", "outlook-callback-test-slug");
        adminId = jdbc.queryForObject(
                "SELECT id FROM admins WHERE slug = 'outlook-callback-test-slug'", Long.class);
    }

    @Test
    @DisplayName("handleCallback saves a CalendarAccount with encrypted tokens and correct email")
    void handleCallback_savesAccountWithEncryptedTokens() {
        stubTokenEndpoint("{\"access_token\":\"at-1\",\"refresh_token\":\"rt-1\",\"token_type\":\"Bearer\"}");
        stubMeEndpoint("{\"id\":\"oid-001\",\"mail\":\"user@contoso.com\"}");

        String state = service.buildState(adminId);
        CalendarAccount saved = service.handleCallback("dummy-code", state);

        assertNotNull(saved.id());
        assertEquals("OUTLOOK", saved.provider());
        assertEquals("oid-001", saved.providerAccountId());
        assertEquals("user@contoso.com", saved.email());
        assertEquals("at-1", encryptionService.decrypt(saved.encryptedAccessToken()));
        assertEquals("rt-1", encryptionService.decrypt(saved.encryptedRefreshToken()));
    }

    @Test
    @DisplayName("handleCallback falls back to userPrincipalName when mail is null")
    void handleCallback_usesUserPrincipalNameWhenMailIsNull() {
        stubTokenEndpoint("{\"access_token\":\"at-2\",\"refresh_token\":\"rt-2\",\"token_type\":\"Bearer\"}");
        stubMeEndpoint("{\"id\":\"oid-002\",\"mail\":null,\"userPrincipalName\":\"personal@outlook.com\"}");

        String state = service.buildState(adminId);
        CalendarAccount saved = service.handleCallback("dummy-code", state);

        assertEquals("personal@outlook.com", saved.email(),
                "userPrincipalName must be used when mail is null");
    }

    @Test
    @DisplayName("handleCallback connecting the same account twice upserts and preserves the row id")
    void handleCallback_reconnectPreservesId() {
        stubTokenEndpoint("{\"access_token\":\"at-3\",\"refresh_token\":\"rt-3\",\"token_type\":\"Bearer\"}");
        stubMeEndpoint("{\"id\":\"oid-003\",\"mail\":\"user@contoso.com\"}");
        String state1 = service.buildState(adminId);
        CalendarAccount first = service.handleCallback("code-1", state1);

        MockRestClientConfig.mockServer.reset();
        stubTokenEndpoint("{\"access_token\":\"at-3b\",\"refresh_token\":\"rt-3b\",\"token_type\":\"Bearer\"}");
        stubMeEndpoint("{\"id\":\"oid-003\",\"mail\":\"user@contoso.com\"}");
        String state2 = service.buildState(adminId);
        CalendarAccount second = service.handleCallback("code-2", state2);

        assertEquals(first.id(), second.id(), "upsert must preserve the original row id");
        assertEquals("at-3b", encryptionService.decrypt(second.encryptedAccessToken()),
                "reconnect must update the access token");
    }

    @Test
    @DisplayName("handleCallback throws IllegalStateException when refresh_token is absent")
    void handleCallback_throwsWhenRefreshTokenAbsent() {
        stubTokenEndpoint("{\"access_token\":\"at-4\",\"token_type\":\"Bearer\"}");
        // /me is never reached — exception should be thrown before the second call
        String state = service.buildState(adminId);

        assertThrows(IllegalStateException.class,
                () -> service.handleCallback("dummy-code", state),
                "Missing refresh_token must throw IllegalStateException");
    }

    @Test
    @DisplayName("handleCallback rejects a tampered state before making any HTTP call")
    void handleCallback_rejectsTamperedState() {
        // No mock expectations set — if any HTTP call were made the test would fail on unexpected request.
        String validState = service.buildState(adminId);
        String tampered = validState.substring(0, validState.length() - 1) +
                (validState.charAt(validState.length() - 1) == 'A' ? 'B' : 'A');

        assertThrows(IllegalArgumentException.class,
                () -> service.handleCallback("dummy-code", tampered));
        MockRestClientConfig.mockServer.verify(); // asserts zero requests were made
    }

    private void stubTokenEndpoint(String body) {
        MockRestClientConfig.mockServer
                .expect(requestTo(org.hamcrest.Matchers.containsString("/oauth2/v2.0/token")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private void stubMeEndpoint(String body) {
        MockRestClientConfig.mockServer
                .expect(requestTo(org.hamcrest.Matchers.containsString("graph.microsoft.com/v1.0/me")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }
}
