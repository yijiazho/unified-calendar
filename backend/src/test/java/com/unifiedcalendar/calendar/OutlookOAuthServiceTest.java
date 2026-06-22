package com.unifiedcalendar.calendar;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("OutlookOAuthService")
class OutlookOAuthServiceTest {

    // Prevent GoogleOAuthService from making HTTP calls (GoogleIdTokenVerifier fetches keys on init).
    @MockBean
    @SuppressWarnings("unused")
    private GoogleOAuthService googleOAuthService;

    // Supply a no-op RestClient so OutlookOAuthService instantiates without a real HTTP client.
    @MockBean(name = "microsoftRestClient")
    @SuppressWarnings("unused")
    private RestClient microsoftRestClient;

    @Autowired
    private OutlookOAuthService service;

    @Test
    @DisplayName("buildAuthorizationUrl contains required query parameters")
    void buildAuthorizationUrl_containsRequiredParams() {
        String url = service.buildAuthorizationUrl(42L);

        assertTrue(url.startsWith("https://login.microsoftonline.com/"),
                "URL must point to Microsoft identity platform");
        assertTrue(url.contains("response_type=code"), "response_type must be code");
        assertTrue(url.contains("offline_access"), "scope must include offline_access");
        assertTrue(url.contains("Calendars.Read"), "scope must include Calendars.Read");
        assertTrue(url.contains("state="), "state parameter must be present");
    }

    @Test
    @DisplayName("buildAuthorizationUrl embeds adminId in the state and the state passes validation")
    void buildAuthorizationUrl_stateIsValidForAdmin() {
        long adminId = 7L;
        String url = service.buildAuthorizationUrl(adminId);

        String state = extractQueryParam(url, "state");
        assertNotNull(state, "state must be present in URL");

        Long extracted = service.validateState(URLDecoder.decode(state, StandardCharsets.UTF_8));
        assertEquals(adminId, extracted);
    }

    @Test
    @DisplayName("validateState rejects a state with a tampered HMAC")
    void validateState_rejectsTamperedHmac() {
        String state = service.buildState(1L);
        // flip the last character of the HMAC segment to corrupt the signature
        String tampered = state.substring(0, state.length() - 1) +
                (state.charAt(state.length() - 1) == 'A' ? 'B' : 'A');

        assertThrows(IllegalArgumentException.class, () -> service.validateState(tampered),
                "Tampered HMAC must be rejected");
    }

    @Test
    @DisplayName("validateState rejects a state that is missing segments")
    void validateState_rejectsMalformedState() {
        assertThrows(IllegalArgumentException.class,
                () -> service.validateState("nocolons"),
                "Single-segment state must be rejected");
    }

    @Test
    @DisplayName("validateState rejects a state whose adminId segment is not numeric")
    void validateState_rejectsNonNumericAdminId() {
        // Craft a state with a non-numeric adminId prefix
        assertThrows(IllegalArgumentException.class,
                () -> service.validateState("notanumber:12345:somefakehmac"),
                "Non-numeric adminId must be rejected");
    }

    // Extracts a single query-parameter value from a URL string (no library dependency).
    private static String extractQueryParam(String url, String name) {
        String prefix = name + "=";
        int start = url.indexOf(prefix);
        if (start < 0) return null;
        start += prefix.length();
        int end = url.indexOf('&', start);
        return end < 0 ? url.substring(start) : url.substring(start, end);
    }
}
