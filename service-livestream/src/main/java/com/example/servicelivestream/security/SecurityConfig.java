package com.example.servicelivestream.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // ✅ EXACTEMENT comme le service notifications
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/ws/**", "/ws/info/**", "/actuator/**")
                )
                // ✅ PAS DE CORS - délégué au Gateway
                .authorizeHttpRequests(auth -> auth
                        // ✅ WebSocket endpoints publics (comme notifications)
                        .requestMatchers("/ws", "/ws/**").permitAll()
                        .requestMatchers("/ws/info", "/ws/info/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        // Webhook endpoint public
                        .requestMatchers("/webhook/**").permitAll()

                        // API endpoints nécessitent authentification
                        .requestMatchers("/api/v1/livestream/**").authenticated()

                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                );

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();

        // ✅ CORRECTION : Validation JWT assouplie pour Gateway
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(issuerUri),
                // ✅ Validation audience plus flexible
                new JwtClaimValidator<List<String>>("aud", aud ->
                        aud != null && (
                                aud.contains("gateway-service") ||
                                        aud.contains("backend-service") ||
                                        aud.contains("account") ||
                                        !aud.isEmpty() // ✅ Accepter toute audience non vide
                        )
                )
        );
        decoder.setJwtValidator(validator);
        return decoder;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            // ✅ CORRECTION : Extraction des rôles Keycloak améliorée
            Map<String, Collection<String>> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess == null || realmAccess.get("roles") == null) {
                return List.of();
            }
            Collection<String> roles = realmAccess.get("roles");
            return roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .collect(Collectors.toList());
        });
        return converter;
    }
}