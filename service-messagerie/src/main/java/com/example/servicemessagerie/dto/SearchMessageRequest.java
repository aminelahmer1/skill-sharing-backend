package com.example.servicemessagerie.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SearchMessageRequest {
    private String query;
    private Long conversationId; // Optionnel: recherche dans une conversation spécifique
    private String messageType; // Optionnel: filtrer par type de message
    private LocalDateTime fromDate; // Optionnel: recherche à partir d'une date
    private LocalDateTime toDate; // Optionnel: recherche jusqu'à une date
}