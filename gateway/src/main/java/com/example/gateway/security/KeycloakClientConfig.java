package com.example.gateway.security;

import feign.RequestInterceptor;
import feign.Retryer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Base64;

@Configuration
public class KeycloakClientConfig {

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    @Bean
    public RequestInterceptor keycloakAuthInterceptor() {
        return template -> template.header("Authorization", getClientCredentialsAuthHeader());
    }

    private String getClientCredentialsAuthHeader() {
        String credentials = clientId + ":" + clientSecret;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
    }

    @Bean
    public Retryer retryer() {
        return new Retryer.Default(1000, 2000, 3); // Retry 3 times with increasing intervals
    }
}
