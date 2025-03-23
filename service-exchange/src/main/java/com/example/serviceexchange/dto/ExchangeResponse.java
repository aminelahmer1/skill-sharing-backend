package com.example.serviceexchange.dto;

import java.time.LocalDateTime;

public record ExchangeResponse(
        Integer id,
        Long providerId,
        Long receiverId,
        Integer skillId,
        String status,
        LocalDateTime createdAt,
        Integer providerRating
) {}