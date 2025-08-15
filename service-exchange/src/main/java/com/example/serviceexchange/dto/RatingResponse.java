package com.example.serviceexchange.dto;

import java.time.LocalDateTime;

public record RatingResponse(
        Integer exchangeId,
        Integer rating,
        String comment,
        LocalDateTime ratingDate,
        String receiverName,
        Long receiverId
) {}