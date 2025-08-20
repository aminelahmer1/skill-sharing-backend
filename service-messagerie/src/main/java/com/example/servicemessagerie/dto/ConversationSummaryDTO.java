package com.example.servicemessagerie.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ConversationSummaryDTO {
    private Long id;
    private String name;
    private String type;
    private int participantCount;
    private int unreadCount;
    private LocalDateTime lastActivity;
    private String lastMessagePreview;
}