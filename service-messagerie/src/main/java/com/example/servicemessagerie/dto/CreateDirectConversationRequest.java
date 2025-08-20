package com.example.servicemessagerie.dto;

import lombok.Data;

@Data
public class CreateDirectConversationRequest {
    private Long currentUserId;
    private Long otherUserId;
}
