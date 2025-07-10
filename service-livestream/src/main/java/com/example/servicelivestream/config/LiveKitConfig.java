package com.example.servicelivestream.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;




@Data
@Configuration
@ConfigurationProperties(prefix = "livekit")
@Validated
public class LiveKitConfig {

    @NotBlank(message = "LiveKit URL is required")
    private String url;

    @NotBlank(message = "LiveKit API key is required")
    private String apiKey;

    @NotBlank(message = "LiveKit API secret is required")
    private String apiSecret;

    @NotBlank(message = "LiveKit server URL is required")
    private String serverUrl;

    private Room room = new Room();
    private Token token = new Token();
    private Recording recording = new Recording();

    @Data
    public static class Room {
        @Positive
        private int maxParticipants = 50;

        @Positive
        private int emptyTimeout = 300; // 5 minutes

        @Positive
        private int maxDuration = 3600; // 1 hour
    }

    @Data
    public static class Token {
        @Positive
        private long ttl = 86400; // 24 hours

        @Positive
        private long publisherTtl = 172800; // 48 hours
    }

    @Data
    public static class Recording {
        private boolean enabled = true;
        private String storagePath = "./recordings";
        private String maxSize = "1GB";
        private String format = "mp4";
    }
}