package com.example.servicemessagerie.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationInviteDTO {
    private Long conversationId;
    private String inviteCode;
    private LocalDateTime expiresAt;
    private int maxUses;
    private int currentUses;
}