package com.example.servicemessagerie.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SearchMessageResultDTO {
    private MessageDTO message;
    private String highlightedContent; // Contenu avec termes de recherche surlignés
    private String conversationName;
    private int matchScore; // Score de pertinence
}