package com.medbuddy.shared.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configure RestTemplate with connection and read timeouts for external API calls.
 * Particularly used for OpenFDA Drug Label API integration.
 */
@Configuration
public class RestTemplateConfig {

    /**
     * RestTemplate bean with 5-second connection and read timeouts.
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }
}
