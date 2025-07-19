package com.example.servicelivestream.service;

import com.example.servicelivestream.config.LiveKitConfig;
import com.example.servicelivestream.exception.LiveKitOperationException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveKitService {
    private final LiveKitConfig liveKitConfig;

    public String generateToken(String userId, String roomName, boolean isPublisher) {
        try {
            validateParameters(userId, roomName);
            log.debug("Generating token for user: {}, room: {}, isPublisher: {}", userId, roomName, isPublisher);
            return buildToken(userId, roomName, isPublisher);
        } catch (JwtException e) {
            log.error("JWT generation failed", e);
            throw new LiveKitOperationException("Token generation failed: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error during token generation", e);
            throw new LiveKitOperationException("Token operation failed", e);
        }
    }

    private String buildToken(String userId, String roomName, boolean isPublisher) {
        byte[] keyBytes = liveKitConfig.getApiSecret().getBytes(StandardCharsets.UTF_8);
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);

        Map<String, Object> header = new HashMap<>();
        header.put("typ", "JWT");
        header.put("alg", "HS256");

        // Utiliser un timestamp correct
        long now = System.currentTimeMillis() / 1000;

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", userId);
        claims.put("name", userId);
        claims.put("iss", liveKitConfig.getApiKey());
        claims.put("nbf", now); // Not before (maintenant)
        claims.put("exp", now + (isPublisher ?
                liveKitConfig.getToken().getPublisherTtl() :
                liveKitConfig.getToken().getTtl())); // Expiration

        claims.put("video", createVideoGrant(roomName, isPublisher));

        return Jwts.builder()
                .setHeader(header)
                .setClaims(claims)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    private Map<String, Object> createVideoGrant(String roomName, boolean isPublisher) {
        Map<String, Object> grant = new HashMap<>();
        grant.put("room", roomName);
        grant.put("roomJoin", true);
        grant.put("canSubscribe", true);
        grant.put("canPublishData", true);
        grant.put("canUpdateOwnMetadata", true);
        grant.put("hidden", false);
        grant.put("recorder", false);

        if (isPublisher) {
            // Le producteur peut publier vidéo, audio et partage d'écran
            grant.put("canPublish", true);
            grant.put("canPublishSources", new String[]{"camera", "microphone", "screen_share"});
        } else {
            // Les viewers peuvent publier leur caméra et micro
            grant.put("canPublish", true);
            grant.put("canPublishSources", new String[]{"camera", "microphone"});
        }

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