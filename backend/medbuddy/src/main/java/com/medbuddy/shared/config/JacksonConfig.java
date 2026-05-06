package com.medbuddy.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Configure Jackson for proper serialization of Java 8 time types.
 * Ensures LocalDate, LocalTime, LocalDateTime serialize to ISO format strings.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // Register Java 8 Time module for proper serialization
        mapper.registerModule(new JavaTimeModule());
        
        // Write dates as ISO strings (e.g., "2024-04-05")
        // instead of timestamps or arrays
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        return mapper;
    }
}
