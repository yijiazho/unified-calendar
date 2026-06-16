package com.unifiedcalendar.config;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GoogleClientConfig {

    /** Production HTTP transport for all Google API calls; replaceable with a mock in tests. */
    @Bean
    public HttpTransport googleHttpTransport() {
        return new NetHttpTransport();
    }
}
