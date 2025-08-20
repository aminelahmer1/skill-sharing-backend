package com.example.servicemessagerie.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TypingIndicatorDTO {
    private Long userId;
    private String userName;
    private Long conversationId;
    private boolean isTyping;
    private LocalDateTime timestamp;
}