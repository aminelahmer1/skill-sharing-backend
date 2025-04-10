package com.example.gateway.controller;

import com.example.gateway.security.KeycloakClient;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
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

        return Mono.fromCallable(() -> keycloakClient.getToken(params))
                .map(response -> ResponseEntity.ok((Object) response.getBody())) // Explicit cast to Object
                .onErrorResume(FeignException.class, e -> Mono.just(
                        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body("Authentication failed: " + e.contentUTF8())
                ))
                .onErrorResume(Exception.class, ex -> Mono.just(
                        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body("An error occurred: " + ex.getMessage())
                ));
    }

}