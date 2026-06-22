package com.unifiedcalendar.calendar;

import com.unifiedcalendar.config.EncryptionService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class OutlookTokenRefresher {

    private final String clientId;
    private final String clientSecret;
    private final String tenantId;
    private final EncryptionService encryptionService;
    private final CalendarAccountRepository repository;
    private final RestClient restClient;

    public OutlookTokenRefresher(
            @Value("${microsoft.client-id}") String clientId,
            @Value("${microsoft.client-secret}") String clientSecret,
            @Value("${microsoft.tenant-id:common}") String tenantId,
            EncryptionService encryptionService,
            CalendarAccountRepository repository,
            @Qualifier("microsoftRestClient") RestClient restClient) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tenantId = tenantId.isBlank() ? "common" : tenantId;
        this.encryptionService = encryptionService;
        this.repository = repository;
        this.restClient = restClient;
    }

    /** Fetches a new access token using the stored refresh token, persists the encrypted value, and returns the plaintext token for immediate use. */
    public String refreshAccessToken(CalendarAccount account) {
        String refreshToken = encryptionService.decrypt(account.encryptedRefreshToken());

        MultiValueMap<String, String> tokenParams = new LinkedMultiValueMap<>();
        tokenParams.add("grant_type", "refresh_token");
        tokenParams.add("refresh_token", refreshToken);
        tokenParams.add("client_id", clientId);
        tokenParams.add("client_secret", clientSecret);
        tokenParams.add("scope", OutlookOAuthService.SCOPE);

        Map<String, Object> tokenResponse = restClient.post()
                .uri("https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token", tenantId)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(tokenParams)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        if (tokenResponse == null) {
            throw new RuntimeException("Microsoft token endpoint returned empty response");
        }
        String newAccessToken = (String) tokenResponse.get("access_token");
        // Microsoft may return a new refresh token (rolling refresh token policy). Persist it when
        // present so the next refresh uses the latest token; fall back to the existing one if absent.
        String newRefreshToken = (String) tokenResponse.get("refresh_token");
        String encryptedRefreshToken = newRefreshToken != null
                ? encryptionService.encrypt(newRefreshToken)
                : account.encryptedRefreshToken();

        CalendarAccount updated = new CalendarAccount(
                account.id(), account.adminId(), account.provider(),
                account.providerAccountId(), account.email(),
                encryptionService.encrypt(newAccessToken),
                encryptedRefreshToken,
                account.isPrimary(), account.connectedAt(), account.lastSyncAt());
        repository.save(updated);
        return newAccessToken;
    }
}
