package com.unifiedcalendar.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class ResendClient {

    private final RestClient restClient;

    public ResendClient(RestClient.Builder restClientBuilder,
                        @Value("${resend.api-key}") String apiKey) {
        this.restClient = restClientBuilder
                .clone()
                .baseUrl("https://api.resend.com")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public void send(SendEmailRequest request) {
        restClient.post()
                .uri("/emails")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }
}
