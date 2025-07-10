package com.example.servicelivestream.service;
/*
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class JitsiTokenService {
    @Value("${jitsi.app-secret}")
    private String appSecret;

    public String generateToken(String userId, String username, boolean isModerator, String roomName) {
        SecretKey key = Keys.hmacShaKeyFor(appSecret.getBytes(StandardCharsets.UTF_8));

        Map<String, Object> headers = new HashMap<>();
        headers.put("kid", "jitsi_custom_kid"); // Correspond à JWT_ACCEPTED_KIDS
        headers.put("typ", "JWT");

        Map<String, Object> context = new HashMap<>();
        context.put("user", Map.of(
                "id", userId,
                "name", username,
                "email", "",
                "avatar", ""
        ));
        context.put("features", Map.of("moderator", isModerator));

        return Jwts.builder()  // Replaced HABbuilder() with Jwts.builder()
                .setHeader(headers)
                .setIssuer("skill_sharing_app") // Correspond à JWT_ACCEPTED_ISSUERS
                .setAudience("meet.jitsi")     // Correspond à JWT_ACCEPTED_AUDIENCES
                .setSubject(roomName)
                .setExpiration(new Date(System.currentTimeMillis() + 12 * 3600 * 1000)) // Expiration dans 12h
                .setIssuedAt(new Date())
                .claim("context", context)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}*/