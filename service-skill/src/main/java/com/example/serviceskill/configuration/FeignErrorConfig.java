package com.example.serviceskill.configuration;

import com.example.serviceskill.exception.SkillNotFoundException;
import feign.FeignException;

import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;

@Configuration
public class FeignErrorConfig {

    @Bean
    public ErrorDecoder errorDecoder() {
        return (methodKey, response) -> {
            if (response.status() == 404) {
                return new SkillNotFoundException("User not found");
            }
            if (response.status() == 403) {
                return new AccessDeniedException("Access denied");
            }
            return FeignException.errorStatus(methodKey, response);
        };
    }
}