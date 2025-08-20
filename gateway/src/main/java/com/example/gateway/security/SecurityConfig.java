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

    // ✅ Endpoints publics
    private static final String[] PUBLIC_ENDPOINTS = {
            "/eureka/**",
            "/actuator/**",
            "/api/v1/auth/**",
            "/api/v1/users/sync",
            "/api/v1/users/register"
    };

    // ✅ WebSocket endpoints - PUBLICS pour connexion initiale + SockJS
    private static final String[] WEBSOCKET_ENDPOINTS = {
            "/ws",
            "/ws/**",
            "/ws/websocket",
            "/ws/info",          // ✅ CRITIQUE : Endpoint SockJS info
            "/ws/info/**",       // ✅ CRITIQUE : Tous les endpoints SockJS info
            "/ws/notifications",
            "/ws/notifications/**",
            "/ws/messaging",     // ✅ Endpoint principal messagerie
            "/ws/messaging/**",  // ✅ Tous les sous-endpoints messagerie (y compris /info)
            "/ws/messages"
    };

    // ✅ Uploads publics (auth déléguée aux services)
    private static final String[] UPLOAD_ENDPOINTS = {
            "/uploads/**",
            "/skill-uploads/**",
            "/message-uploads/**"
    };

    // ✅ LiveKit endpoints
    private static final String[] LIVEKIT_ENDPOINTS = {
            "/rtc",
            "/rtc/**",
            "/livekit",
            "/livekit/**"
    };

    // ✅ API endpoints authentifiés
    private static final String[] AUTHENTICATED_ENDPOINTS = {
            "/api/v1/skills/**",
            "/api/v1/exchanges/**",
            "/api/v1/livestream/**",
            "/api/v1/users/**",
            "/api/v1/notifications/**",
            "/api/v1/messages/**" // ✅ Messagerie API (pas WebSocket)
    };

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                // ✅ Désactiver CSRF pour les APIs
                .csrf(ServerHttpSecurity.CsrfSpec::disable)

                // ✅ Configuration CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // ✅ Configuration des autorisations
                .authorizeExchange(exchange -> exchange
                        // ✅ OPTIONS toujours autorisées (CORS preflight)
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()

                        // ✅ Endpoints publics
                        .pathMatchers(PUBLIC_ENDPOINTS).permitAll()

                        // ✅ WebSocket endpoints - PUBLICS (CRUCIAL pour SockJS)
                        // L'authentification se fait dans l'interceptor WebSocket du service
                        .pathMatchers(WEBSOCKET_ENDPOINTS).permitAll()

                        // ✅ Uploads publics (auth déléguée aux services)
                        .pathMatchers(UPLOAD_ENDPOINTS).permitAll()

                        // ✅ LiveKit endpoints publics
                        .pathMatchers(LIVEKIT_ENDPOINTS).permitAll()

                        // ✅ API endpoints authentifiés
                        .pathMatchers(AUTHENTICATED_ENDPOINTS).authenticated()

                        // ✅ Tout le reste authentifié
                        .anyExchange().authenticated()
                )

                // ✅ Configuration OAuth2 Resource Server
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

        // ✅ Credentials autorisés
        config.setAllowCredentials(true);

        // ✅ Origins autorisés (WebSocket + HTTP)
        config.setAllowedOriginPatterns(List.of("http://localhost:4200", "ws://localhost:4200"));

        // ✅ Méthodes autorisées
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));

        // ✅ Headers autorisés
        config.setAllowedHeaders(List.of("*"));

        // ✅ Headers exposés
        config.setExposedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));

        // ✅ Cache CORS
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // ✅ Convertisseur JWT réactif
    private Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtReactiveAuthenticationConverter() {
        return jwt -> {
            JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
            converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
            converter.setPrincipalClaimName("sub"); // Support UUID et Long
            return Mono.just(converter.convert(jwt));
        };
    }
}