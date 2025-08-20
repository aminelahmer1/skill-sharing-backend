package com.example.servicemessagerie.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ConversationDTO {
    private Long id;
    private String name;
    private String type; // DIRECT, GROUP, SKILL_GROUP
    private String status; // ACTIVE, ARCHIVED, COMPLETED, CANCELLED
    private Integer skillId; // Pour les conversations de compétence

    private List<ParticipantDTO> participants;
    private String lastMessage;
    private LocalDateTime lastMessageTime;
    private int unreadCount;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Métadonnées supplémentaires
    private boolean canSendMessage; // L'utilisateur peut-il envoyer des messages ?
    private boolean isAdmin; // L'utilisateur est-il admin de la conversation ?
    private String conversationAvatar; // Avatar pour les conversations de groupe
}
