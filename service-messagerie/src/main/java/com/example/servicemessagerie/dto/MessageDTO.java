package com.example.servicemessagerie.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
public class MessageDTO {
    private Long id;
    private Long conversationId;
    private Long senderId;
    private String senderName;
    private String senderAvatar;

    private String content;
    private String type; // TEXT, IMAGE, FILE, AUDIO, VIDEO, SYSTEM
    private String status; // SENT, DELIVERED, READ

    private String attachmentUrl;
    private String attachmentName;
    private String attachmentType;
    private Long attachmentSize;

    private LocalDateTime sentAt;
    private LocalDateTime readAt;
    private LocalDateTime editedAt;
    private boolean isDeleted;

    // Métadonnées
    private boolean canEdit; // L'utilisateur peut-il éditer ce message ?
    private boolean canDelete; // L'utilisateur peut-il supprimer ce message ?
    private int replyToMessageId; // ID du message auquel celui-ci répond (si applicable)
}