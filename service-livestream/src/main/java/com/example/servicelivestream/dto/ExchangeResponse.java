package com.example.servicelivestream.dto;


import java.time.LocalDateTime;

public record ExchangeResponse(
        Integer id,
        Long producerId,
        Long receiverId,
        Integer skillId,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime streamingDate,
        Integer producerRating,
        String rejectionReason,
        String skillName,
        String receiverName,
        Integer skillIdField
) {}