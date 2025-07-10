package com.example.servicelivestream.service;

import com.example.servicelivestream.config.LiveKitConfig;
import io.livekit.server.AccessToken;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class LiveKitService {
    private final LiveKitConfig liveKitConfig;

    public LiveKitService(LiveKitConfig liveKitConfig) {
        this.liveKitConfig = liveKitConfig;
    }

    public String generateToken(String userId, String roomName, boolean isPublisher) {
        // Création du token avec la clé API et le secret
        AccessToken token = new AccessToken(liveKitConfig.getApiKey(), liveKitConfig.getApiSecret());

        // Configuration des claims du token
        token.setIdentity(userId);
        token.setName(userId);

        // Durée de validité du token (24 heures)
        token.setTtl(24 * 60 * 60);

        // Configuration des permissions
        Map<String, Object> grants = new HashMap<>();
        grants.put("room", roomName);
        grants.put("roomJoin", true);
        grants.put("canPublish", isPublisher);
        grants.put("canSubscribe", true);

        // Pour la version 0.9.0, nous utilisons setMetadata pour les grants
        token.setMetadata(grants.toString());

        return token.toJwt();
    }

    public String getWebSocketUrl() {
        return liveKitConfig.getUrl();
    }
}