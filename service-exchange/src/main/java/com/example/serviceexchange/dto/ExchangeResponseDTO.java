package com.example.serviceexchange.dto;

import java.time.LocalDateTime;

public record ExchangeResponseDTO(
        Integer id,
        Long producerId,
        Long receiverId,
        Integer skillId,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime streamingDate,
        Integer producerRating,
        String skillName,
        String receiverName
) {}