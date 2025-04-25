package com.example.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/eureka/**",
            "/actuator/**",
            "/api/v1/auth/**",
            "/api/v1/users/sync",
            "/api/v1/users/test"
    };

    private static final String[] PRODUCER_RECEIVER_ENDPOINTS = {
            "/api/v1/skills/**",
            "/api/v1/exchanges/**"
    };

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .pathMatchers(PRODUCER_RECEIVER_ENDPOINTS).hasAnyRole("PRODUCER", "RECEIVER")
                        .pathMatchers("/api/v1/users/**").authenticated()
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtReactiveAuthenticationConverter())));
        return http.build();
    }

    private Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtReactiveAuthenticationConverter() {
        return jwt -> {
            JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
            converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
            return Mono.just(converter.convert(jwt));
        };
    }
}