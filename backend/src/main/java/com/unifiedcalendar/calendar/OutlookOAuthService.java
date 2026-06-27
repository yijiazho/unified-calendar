package com.unifiedcalendar.calendar;

import com.unifiedcalendar.config.EncryptionService;
import com.unifiedcalendar.calendar.Provider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import org.springframework.core.ParameterizedTypeReference;

import java.util.Map;

@Service
public class OutlookOAuthService {

    private static final long STATE_TTL_SECONDS = 10 * 60;
    // Package-private so OutlookTokenRefresher can reference the same string and avoid drift.
    static final String SCOPE =
            "https://graph.microsoft.com/Calendars.Read " +
            "https://graph.microsoft.com/Calendars.ReadWrite " +
            "offline_access User.Read";

    private final String clientId;
    private final String clientSecret;
    private final String tenantId;
    private final String redirectUri;
    private final byte[] hmacKeyBytes;
    private final EncryptionService encryptionService;
    private final CalendarAccountRepository repository;
    private final RestClient restClient;

    public OutlookOAuthService(
            @Value("${microsoft.client-id}") String clientId,
            @Value("${microsoft.client-secret}") String clientSecret,
            @Value("${microsoft.tenant-id:common}") String tenantId,
            @Value("${microsoft.redirect-uri:http://localhost:8080/calendar/outlook/callback}") String redirectUri,
            @Value("${encryption.secret-key}") String rawKey,
            EncryptionService encryptionService,
            CalendarAccountRepository repository,
            @Qualifier("microsoftRestClient") RestClient restClient) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tenantId = tenantId.isBlank() ? "common" : tenantId;
        this.redirectUri = redirectUri;
        // Domain-separated HMAC key: SHA-256("oauth-state-hmac:" || rawKey) so the key
        // material is independent of the AES-256 key used by EncryptionService.
        this.hmacKeyBytes = deriveHmacKey(rawKey);
        this.encryptionService = encryptionService;
        this.repository = repository;
        this.restClient = restClient;
    }

    /** Builds the Microsoft OAuth2 authorization URL; state binds the request to adminId via HMAC to prevent CSRF. */
    public String buildAuthorizationUrl(Long adminId) {
        String state = buildState(adminId);
        return UriComponentsBuilder
                .fromUriString("https://login.microsoftonline.com/{tenant}/oauth2/v2.0/authorize")
                .queryParam("client_id", clientId)
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", SCOPE)
                .queryParam("state", state)
                .buildAndExpand(tenantId)
                .toUriString();
    }

    /** Validates state, exchanges the code for tokens, retrieves the user's oid and email via Graph, and upserts a calendar_account row. */
    public CalendarAccount handleCallback(String code, String state) {
        Long adminId = validateState(state);

        MultiValueMap<String, String> tokenParams = new LinkedMultiValueMap<>();
        tokenParams.add("grant_type", "authorization_code");
        tokenParams.add("code", code);
        tokenParams.add("client_id", clientId);
        tokenParams.add("client_secret", clientSecret);
        tokenParams.add("redirect_uri", redirectUri);

        Map<String, Object> tokenResponse = restClient.post()
                .uri("https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token", tenantId)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(tokenParams)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        if (tokenResponse == null) {
            throw new RuntimeException("Microsoft token endpoint returned empty response");
        }
        String accessToken = (String) tokenResponse.get("access_token");
        String refreshToken = (String) tokenResponse.get("refresh_token");

        // Microsoft omits refresh_token when offline_access was not granted or the token was already issued.
        // Storing null would cause a NullPointerException in EncryptionService on the next sync.
        if (refreshToken == null) {
            throw new IllegalStateException(
                    "Microsoft did not return a refresh token. Ensure offline_access is in the scope, " +
                    "or revoke access at https://myaccount.microsoft.com/permissions and reconnect.");
        }

        Map<String, Object> me = restClient.get()
                .uri("https://graph.microsoft.com/v1.0/me")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        if (me == null) {
            throw new RuntimeException("Microsoft Graph /me returned empty response");
        }
        String oid = (String) me.get("id");
        // mail is null for some account types (e.g. personal accounts use userPrincipalName)
        String email = (String) me.get("mail");
        if (email == null || email.isBlank()) {
            email = (String) me.get("userPrincipalName");
        }

        CalendarAccount account = new CalendarAccount(
                null, adminId, Provider.OUTLOOK, oid, email,
                encryptionService.encrypt(accessToken),
                encryptionService.encrypt(refreshToken),
                false, Instant.now(), null, null);
        return repository.save(account);
    }

    // state = "{adminId}:{issuedAt}:{base64url(HMAC-SHA256("{adminId}:{issuedAt}", hmacKey))}"
    String buildState(Long adminId) {
        long issuedAt = Instant.now().getEpochSecond();
        String data = adminId + ":" + issuedAt;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKeyBytes, "HmacSHA256"));
            byte[] hmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return data + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(hmac);
        } catch (Exception e) {
            throw new RuntimeException("State generation failed", e);
        }
    }

    Long validateState(String state) {
        int lastColon = state.lastIndexOf(':');
        if (lastColon < 0) throw new IllegalArgumentException("Malformed OAuth state");
        String receivedHmac = state.substring(lastColon + 1);
        String data         = state.substring(0, lastColon); // "{adminId}:{issuedAt}"

        int firstColon = data.indexOf(':');
        if (firstColon < 0) throw new IllegalArgumentException("Malformed OAuth state");
        String adminIdStr  = data.substring(0, firstColon);
        String issuedAtStr = data.substring(firstColon + 1);

        Long adminId;
        try {
            adminId = Long.parseLong(adminIdStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Malformed OAuth state");
        }
        long issuedAt;
        try {
            issuedAt = Long.parseLong(issuedAtStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Malformed OAuth state");
        }

        // Verify HMAC first — reject forgeries before revealing TTL information
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKeyBytes, "HmacSHA256"));
            byte[] expected = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            byte[] received = Base64.getUrlDecoder().decode(receivedHmac);
            if (!MessageDigest.isEqual(expected, received)) {
                throw new IllegalArgumentException("Invalid OAuth state signature");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("State validation failed", e);
        }

        // Enforce TTL only after the signature is verified
        long now = Instant.now().getEpochSecond();
        if (now - issuedAt > STATE_TTL_SECONDS) {
            throw new IllegalArgumentException("OAuth state has expired");
        }
        if (issuedAt > now + 60) {
            throw new IllegalArgumentException("OAuth state timestamp is in the future");
        }

        return adminId;
    }

    private static byte[] deriveHmacKey(String rawKey) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update("oauth-state-hmac:".getBytes(StandardCharsets.UTF_8));
            return sha.digest(rawKey.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
