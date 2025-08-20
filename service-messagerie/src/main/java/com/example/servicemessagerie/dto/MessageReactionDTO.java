package com.example.servicemessagerie.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MessageReactionDTO {
    private Long messageId;
    private Long userId;
    private String userName;
    private String reaction; // 👍, ❤️, 😀, etc.
    private LocalDateTime timestamp;
}