package com.example.gateway.controller;

import com.example.gateway.security.KeycloakClient;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final KeycloakClient keycloakClient;

    @Value("${keycloak.auth-server-url}")
    private String authServerUrl;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    @GetMapping("/login")
    public Mono<String> login() {
        String authUrl = String.format(
                "%s/realms/%s/protocol/openid-connect/auth?" +
                        "client_id=%s&" +
                        "redirect_uri=http://localhost:8822/auth/callback&" +
                        "response_type=code&" +
                        "scope=openid profile email",
                authServerUrl,
                "skill-sharing",
                clientId
        );
        return Mono.just(authUrl);
    }

    @PostMapping("/callback")
    public Mono<ResponseEntity<Object>> callback(@RequestParam String code) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("code", code);
        params.add("redirect_uri", "http://localhost:8822/auth/callback");

        return Mono.fromCallable(() -> {
                    log.debug("Sending callback token request with params: {}", params);
                    return keycloakClient.getToken(params);
                })
                .map(response -> {
                    log.info("Callback token request successful");
                    return ResponseEntity.ok((Object) response.getBody());
                })
                .onErrorResume(FeignException.class, e -> {
                    log.error("Callback error: {}", e.contentUTF8(), e);
                    return Mono.just(
                            ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                    .body("Authentication failed: " + e.contentUTF8())
                    );
                })
                .onErrorResume(Exception.class, ex -> {
                    log.error("Unexpected callback error: {}", ex.getMessage(), ex);
                    return Mono.just(
                            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                    .body("An error occurred: " + ex.getMessage())
                    );
                });
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<Map<String, String>>> login(@RequestBody LoginRequest request) {
        log.info("Login attempt for username: {}", request.getUsername());
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "password");
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("username", request.getUsername());
        params.add("password", request.getPassword());

        return Mono.fromCallable(() -> {
                    log.debug("Sending token request with params: {}", params);
                    return keycloakClient.getToken(params);
                })
                .map(response -> {
                    log.info("Token request successful: {}", response.getBody());
                    Map<String, String> tokenResponse = new HashMap<>();
                    tokenResponse.put("token", (String) response.getBody().get("access_token"));
                    return ResponseEntity.ok(tokenResponse);
                })
                .onErrorResume(FeignException.class, e -> {
                    log.error("FeignException during login: {}", e.contentUTF8(), e);
                    return Mono.just(
                            ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                    .body(Map.of("error", "Authentication failed: " + e.contentUTF8()))
                    );
                })
                .onErrorResume(Exception.class, ex -> {
                    log.error("Unexpected error during login: {}", ex.getMessage(), ex);
                    return Mono.just(
                            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                    .body(Map.of("error", "An error occurred: " + ex.getMessage()))
                    );
                });
    }

    public static class LoginRequest {
        private String username;
        private String password;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}