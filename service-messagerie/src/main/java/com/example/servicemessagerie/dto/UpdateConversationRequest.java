package com.example.servicemessagerie.dto;

import lombok.Data;

@Data
public class UpdateConversationRequest {
    private String name;
    private String description;
}