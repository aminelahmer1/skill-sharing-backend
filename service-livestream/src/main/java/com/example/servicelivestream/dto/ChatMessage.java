package com.example.servicelivestream.dto;

import java.time.LocalDateTime;

public record ChatMessage(
        String userId,
        String username,
        String message,
        LocalDateTime timestamp
) {
    public ChatMessage(String userId, String username, String message) {
        this(userId, username, message, LocalDateTime.now());
    }
}