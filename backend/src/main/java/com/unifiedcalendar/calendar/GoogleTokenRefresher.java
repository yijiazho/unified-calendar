package com.unifiedcalendar.calendar;

import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.unifiedcalendar.config.EncryptionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GoogleTokenRefresher {

    private final String clientId;
    private final String clientSecret;
    private final HttpTransport httpTransport;
    private final EncryptionService encryptionService;

    public GoogleTokenRefresher(
            @Value("${google.client-id}") String clientId,
            @Value("${google.client-secret}") String clientSecret,
            HttpTransport googleHttpTransport,
            EncryptionService encryptionService) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.httpTransport = googleHttpTransport;
        this.encryptionService = encryptionService;
    }

    /** Fetches a new access token using the stored refresh token and returns the result for the caller to persist. */
    public TokenRefreshResult refreshAccessToken(CalendarAccount account) {
        String refreshToken = encryptionService.decrypt(account.encryptedRefreshToken());

        GoogleTokenResponse tokenResponse;
        try {
            tokenResponse = new GoogleRefreshTokenRequest(
                    httpTransport, GsonFactory.getDefaultInstance(),
                    refreshToken, clientId, clientSecret)
                    .execute();
        } catch (Exception e) {
            throw new RuntimeException("Google token refresh failed", e);
        }

        String newAccessToken = tokenResponse.getAccessToken();
        CalendarAccount updated = new CalendarAccount(
                account.id(), account.adminId(), account.provider(),
                account.providerAccountId(), account.email(),
                encryptionService.encrypt(newAccessToken),
                account.encryptedRefreshToken(),
                account.isPrimary(), account.connectedAt(), account.lastSyncAt(), null);
        return new TokenRefreshResult(newAccessToken, updated);
    }
}
