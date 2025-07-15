package com.example.servicelivestream.service;

import com.example.servicelivestream.config.LiveKitConfig;
import com.example.servicelivestream.exception.LiveKitOperationException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveKitService {
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private final LiveKitConfig liveKitConfig;
    private final RestTemplate restTemplate;

    @Retryable(value = {LiveKitOperationException.class},
            maxAttempts = MAX_RETRY_ATTEMPTS,
            backoff = @Backoff(delay = RETRY_DELAY_MS))
    public String generateAndValidateToken(String userId, String roomName, boolean isPublisher) {
        try {
            validateParameters(userId, roomName);

            // Ajout de logs pour le débogage
            log.debug("Generating token for user: {}, room: {}, isPublisher: {}", userId, roomName, isPublisher);

            String token = generateToken(userId, roomName, isPublisher);

            // Validation plus robuste
            validateWithLiveKitServer(token);

            return token;
        } catch (JwtException e) {
            log.error("JWT generation failed", e);
            throw new LiveKitOperationException("Token generation failed: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error during token generation", e);
            throw new LiveKitOperationException("Token operation failed", e);
        }
    }

    public String generateToken(String userId, String roomName, boolean isPublisher) {
        byte[] keyBytes = liveKitConfig.getApiSecret().getBytes(StandardCharsets.UTF_8);
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);

        // Header
        Map<String, Object> header = new HashMap<>();
        header.put("typ", "JWT");
        header.put("alg", "HS256");

        // Claims
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", userId);
        claims.put("name", userId);
        claims.put("iss", liveKitConfig.getApiKey());
        claims.put("nbf", Date.from(Instant.now()));
        claims.put("exp", Date.from(Instant.now().plusSeconds(liveKitConfig.getToken().getTtl())));

        // Video Grants selon la documentation LiveKit
        Map<String, Object> video = new HashMap<>();
        video.put("roomJoin", true);
        video.put("room", roomName);
        video.put("canPublish", isPublisher);
        video.put("canSubscribe", true);
        video.put("canPublishData", true);

        claims.put("video", video);

        return Jwts.builder()
                .setHeader(header) // Définir explicitement le header
                .setClaims(claims)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    private void validateWithLiveKitServer(String token) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-LiveKit-Version", "1.0");

            HttpEntity<?> entity = new HttpEntity<>(headers);
            String validateUrl = liveKitConfig.getServerUrl() + "/rtc/validate";

            ResponseEntity<String> response = restTemplate.exchange(
                    validateUrl,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new LiveKitOperationException(
                        "Token validation failed with status: " + response.getStatusCode()
                );
            }

            // Vérification plus poussée de la réponse
            if (response.getBody() == null || !response.getBody().contains("\"valid\":true")) {
                log.error("Invalid token validation response: {}", response.getBody());
                throw new LiveKitOperationException("Token validation response was invalid");
            }

            log.debug("Token validated successfully");
        } catch (HttpClientErrorException e) {
            log.error("HTTP error during validation: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new LiveKitOperationException("HTTP error during validation", e);
        } catch (Exception e) {
            log.error("Validation error", e);
            throw new LiveKitOperationException("Token validation failed", e);
        }
    }
    private Map<String, Object> createVideoGrant(String roomName, boolean isPublisher) {
        Map<String, Object> grant = new HashMap<>();
        grant.put("room", roomName);
        grant.put("roomJoin", true);
        grant.put("canPublish", isPublisher);
        grant.put("canSubscribe", true);
        grant.put("canPublishData", true);
        grant.put("recorder", isPublisher);
        grant.put("hidden", false);
        return grant;
    }

    public void validateParameters(String userId, String roomName) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        if (roomName == null || roomName.isBlank()) {
            throw new IllegalArgumentException("Room name cannot be null or empty");
        }
        if (liveKitConfig.getApiKey() == null || liveKitConfig.getApiKey().isBlank()) {
            throw new IllegalStateException("LiveKit API key is not configured");
        }
        if (liveKitConfig.getApiSecret() == null || liveKitConfig.getApiSecret().isBlank()) {
            throw new IllegalStateException("LiveKit API secret is not configured");
        }
    }
}