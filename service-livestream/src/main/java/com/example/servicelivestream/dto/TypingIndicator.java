package com.example.servicelivestream.dto;

import java.time.LocalDateTime;

public record TypingIndicator(
        String userId,
        String username,
        boolean isTyping,
        LocalDateTime timestamp
) {
    public TypingIndicator(String userId, String username, boolean isTyping) {
        this(userId, username, isTyping, LocalDateTime.now());
    }
}