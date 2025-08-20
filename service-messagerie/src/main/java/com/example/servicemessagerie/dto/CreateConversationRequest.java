package com.example.servicemessagerie.dto;

import lombok.Data;

import java.util.List;

@Data
public class CreateConversationRequest {
    private String name;
    private List<Long> participantIds;
    private String description;
}