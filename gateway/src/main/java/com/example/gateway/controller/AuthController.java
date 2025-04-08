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
import org.springframework.web.servlet.view.RedirectView;

import java.util.Map;

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
    public RedirectView login() {
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
        return new RedirectView(authUrl);
    }

    @PostMapping("/callback")
    public ResponseEntity<?> callback(@RequestParam String code) {
        try {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("grant_type", "authorization_code");
            params.add("client_id", clientId);
            params.add("client_secret", clientSecret);
            params.add("code", code);
            params.add("redirect_uri", "http://localhost:8822/auth/callback");

            ResponseEntity<Map<String, Object>> response = keycloakClient.getToken(params);
            return ResponseEntity.ok(response.getBody());
        } catch (FeignException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Authentication failed: " + e.contentUTF8());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred: " + ex.getMessage());
        }
    }
}
