package com.example.servicemessagerie.dto;

import lombok.Data;

@Data
public class MessageRequest {
    private Long conversationId;
    private Long senderId;
    private String content;
    private String type; // TEXT, IMAGE, FILE, etc.
    private String attachmentUrl;
    private Integer replyToMessageId; // Pour les réponses
}