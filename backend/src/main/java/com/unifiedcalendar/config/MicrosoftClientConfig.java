package com.unifiedcalendar.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class MicrosoftClientConfig {

    /** Provides the shared RestClient instance used for all Microsoft Graph and Entra ID HTTP calls. */
    @Bean(name = "microsoftRestClient")
    public RestClient microsoftRestClient(RestClient.Builder builder) {
        return builder.build();
    }
}
