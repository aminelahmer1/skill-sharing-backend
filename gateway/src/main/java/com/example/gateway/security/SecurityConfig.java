package com.example.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/eureka/**",  // Eureka server
            "/actuator/**",  // Health checks
            "/api/v1/auth/**",  // Authentication endpoints
            "/api/v1/users/sync",  // Allow public access for testing
            "/api/v1/users/*"
    };

    private static final String[] PROVIDER_RECEIVER_ENDPOINTS = {
            "/api/v1/skills/**",
            "/api/v1/exchanges/**"
    };



    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/users/sync", "/api/v1/auth/**").permitAll()  // Public endpoints
                        .requestMatchers(PROVIDER_RECEIVER_ENDPOINTS).hasAnyRole("PROVIDER", "RECEIVER")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }


    private Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return converter;
    }
}
