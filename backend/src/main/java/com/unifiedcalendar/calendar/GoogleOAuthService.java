package com.unifiedcalendar.calendar;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.unifiedcalendar.config.EncryptionService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Service
public class GoogleOAuthService {

    private static final long STATE_TTL_SECONDS = 10 * 60; // 10 minutes
    private static final List<String> SCOPES = List.of(
            "openid",
            "email",
            "https://www.googleapis.com/auth/calendar.readonly",
            "https://www.googleapis.com/auth/calendar.events"
    );

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final byte[] hmacKeyBytes;
    private final HttpTransport httpTransport;
    private final GoogleIdTokenVerifier idTokenVerifier;
    private final EncryptionService encryptionService;
    private final CalendarAccountRepository repository;

    public GoogleOAuthService(
            @Value("${google.client-id}") String clientId,
            @Value("${google.client-secret}") String clientSecret,
            @Value("${google.redirect-uri:http://localhost:8080/calendar/google/callback}") String redirectUri,
            @Value("${encryption.secret-key}") String rawKey,
            HttpTransport googleHttpTransport,
            EncryptionService encryptionService,
            CalendarAccountRepository repository) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        // Domain-separated HMAC key: SHA-256("oauth-state-hmac:" || rawKey) so the key
        // material is independent of the AES-256 key used by EncryptionService.
        this.hmacKeyBytes = deriveHmacKey(rawKey);
        this.httpTransport = googleHttpTransport;
        // Verifier is built once; Google's public keys are fetched lazily on first use and cached.
        this.idTokenVerifier = new GoogleIdTokenVerifier.Builder(googleHttpTransport, GsonFactory.getDefaultInstance())
                .setAudience(List.of(clientId))
                .build();
        this.encryptionService = encryptionService;
        this.repository = repository;
    }

    /** Builds the Google OAuth2 authorization URL; the state parameter binds the request to adminId via HMAC to prevent CSRF. */
    public String buildAuthorizationUrl(Long adminId) {
        String state = buildState(adminId);
        return new GoogleAuthorizationCodeRequestUrl(clientId, redirectUri, SCOPES)
                .setState(state)
                .setAccessType("offline")
                .set("prompt", "consent")
                .build();
    }

    /** Validates state, exchanges the code for tokens, extracts identity from the ID token, and upserts a calendar_account row. */
    public CalendarAccount handleCallback(String code, String state) {
        Long adminId = validateState(state);

        GoogleTokenResponse tokenResponse;
        try {
            tokenResponse = new GoogleAuthorizationCodeTokenRequest(
                    httpTransport, GsonFactory.getDefaultInstance(),
                    clientId, clientSecret, code, redirectUri)
                    .execute();
        } catch (Exception e) {
            throw new RuntimeException("Google token exchange failed", e);
        }

        GoogleIdToken.Payload payload;
        try {
            GoogleIdToken verified = idTokenVerifier.verify(tokenResponse.getIdToken());
            if (verified == null) {
                // null means signature, audience, issuer, or expiry check failed
                throw new RuntimeException("Google ID token verification failed — untrusted token");
            }
            payload = verified.getPayload();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Google ID token verification failed", e);
        }

        String sub          = payload.getSubject();
        String email        = payload.getEmail();
        String accessToken  = tokenResponse.getAccessToken();
        String refreshToken = tokenResponse.getRefreshToken();

        // Google only returns a refresh token on the first authorization or after access has been revoked.
        // Storing an empty token would cause silent failures on the next refresh attempt.
        if (refreshToken == null) {
            throw new IllegalStateException(
                    "Google did not return a refresh token. Ensure access_type=offline and prompt=consent " +
                    "are set, or revoke access at https://myaccount.google.com/permissions and reconnect.");
        }

        CalendarAccount account = new CalendarAccount(
                null, adminId, "GOOGLE", sub, email,
                encryptionService.encrypt(accessToken),
                encryptionService.encrypt(refreshToken),
                false, Instant.now(), null);
        return repository.save(account);
    }

    // state = "{adminId}:{issuedAt}:{base64url(HMAC-SHA256("{adminId}:{issuedAt}", hmacKey))}"
    // issuedAt is epoch seconds; validated against STATE_TTL_SECONDS in validateState.
    private String buildState(Long adminId) {
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

    private Long validateState(String state) {
        // state format: {adminId}:{issuedAt}:{base64url(HMAC)}
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
