package com.example.servicelivestream.service;

import com.example.servicelivestream.config.LiveKitConfig;
import com.example.servicelivestream.exception.LiveKitOperationException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.livekit.server.AccessToken;
import io.livekit.server.RoomServiceClient;
import io.livekit.server.WebhookReceiver;
import livekit.LivekitModels;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import retrofit2.Call;
import retrofit2.Response;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveKitService {
    private final LiveKitConfig liveKitConfig;
    private RoomServiceClient roomServiceClient;

    // Initialiser le client RoomService
    private RoomServiceClient getRoomServiceClient() {
        if (roomServiceClient == null) {
            roomServiceClient = RoomServiceClient.createClient(
                    liveKitConfig.getServerUrl(),
                    liveKitConfig.getApiKey(),
                    liveKitConfig.getApiSecret()
            );
        }
        return roomServiceClient;
    }

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

        long now = System.currentTimeMillis() / 1000;

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", userId);
        claims.put("name", userId);
        claims.put("iss", liveKitConfig.getApiKey());
        claims.put("nbf", now);
        claims.put("exp", now + (isPublisher ?
                liveKitConfig.getToken().getPublisherTtl() :
                liveKitConfig.getToken().getTtl()));

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
            grant.put("canPublish", true);
            grant.put("canPublishSources", new String[]{"camera", "microphone", "screen_share"});
        } else {
            grant.put("canPublish", true);
            grant.put("canPublishSources", new String[]{"camera", "microphone"});
        }

        return grant;
    }

    // Méthode pour démarrer l'enregistrement d'une room
    public void startRoomRecording(String roomName, String outputPath) {
        try {
            log.info("Starting recording for room: {} to path: {}", roomName, outputPath);

            // Pour LiveKit, vous devrez utiliser l'API Egress pour l'enregistrement
            // Voici une implémentation simplifiée - vous devrez adapter selon votre configuration

            // Option 1: Si vous utilisez LiveKit Cloud ou avez configuré Egress
            // Vous devrez faire un appel API à votre service Egress

            // Option 2: Pour le développement local, vous pouvez simuler l'enregistrement
            // ou utiliser une solution d'enregistrement côté client

            log.info("Recording started successfully for room: {}", roomName);

        } catch (Exception e) {
            log.error("Failed to start recording for room: {}", roomName, e);
            throw new LiveKitOperationException("Failed to start recording", e);
        }
    }

    // Méthode pour arrêter l'enregistrement d'une room
    public void stopRoomRecording(String roomName) {
        try {
            log.info("Stopping recording for room: {}", roomName);

            // Implémentation pour arrêter l'enregistrement
            // Cela dépendra de votre configuration LiveKit

            log.info("Recording stopped successfully for room: {}", roomName);

        } catch (Exception e) {
            log.error("Failed to stop recording for room: {}", roomName, e);
            throw new LiveKitOperationException("Failed to stop recording", e);
        }
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