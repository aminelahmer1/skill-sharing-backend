package com.example.servicemessagerie.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ConversationStatsDTO {
    private Long conversationId;
    private int totalMessages;
    private int totalParticipants;
    private int activeParticipants;
    private LocalDateTime firstMessageDate;
    private LocalDateTime lastMessageDate;
    private int messagesLastWeek;
    private int messagesLastMonth;
}