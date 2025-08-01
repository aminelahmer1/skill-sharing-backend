package com.example.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.util.List;

@Configuration
@EnableWebFluxSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/eureka/**",
            "/actuator/**",
            "/api/v1/auth/**",
            "/api/v1/users/sync",
            "/api/v1/users/register",
            "/uploads/**",
            "/skill-uploads/**"
    };

    private static final String[] WEBSOCKET_ENDPOINTS = {
            "/ws",
            "/ws/**",
            "/ws/websocket",
            "/ws/info",
            "/ws/info/**",
            "/ws/notifications",
            "/ws/notifications/**"
    };

    private static final String[] LIVEKIT_ENDPOINTS = {
            "/rtc",
            "/rtc/**",
            "/livekit",
            "/livekit/**"
    };

    private static final String[] AUTHENTICATED_ENDPOINTS = {
            "/api/v1/skills/**",
            "/api/v1/exchanges/**",
            "/api/v1/livestream/**",
            "/api/v1/users/**",
            "/api/v1/notifications/**"
    };

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeExchange(exchange -> exchange
                        // ✅ Options requests toujours autorisées
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()

                        // ✅ Endpoints publics
                        .pathMatchers(PUBLIC_ENDPOINTS).permitAll()

                        // ✅ WebSocket endpoints - PUBLICS pour l'établissement de connexion
                        // L'authentification se fait dans l'interceptor WebSocket
                        .pathMatchers(WEBSOCKET_ENDPOINTS).permitAll()

                        // ✅ LiveKit endpoints publics
                        .pathMatchers(LIVEKIT_ENDPOINTS).permitAll()

                        // ✅ API endpoints nécessitent authentification
                        .pathMatchers(AUTHENTICATED_ENDPOINTS).authenticated()

                        // ✅ Tout le reste nécessite authentification
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(jwtReactiveAuthenticationConverter())
                        )
                )
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOrigins(List.of("http://localhost:4200"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtReactiveAuthenticationConverter() {
        return jwt -> {
            JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
            converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
            return Mono.just(converter.convert(jwt));
        };
    }
}