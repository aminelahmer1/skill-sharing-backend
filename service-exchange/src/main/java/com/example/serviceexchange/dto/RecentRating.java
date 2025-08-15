package com.example.serviceexchange.dto;

import java.time.LocalDateTime;

public record RecentRating(
        Integer exchangeId,
        String skillName,
        String receiverName,
        Integer rating,
        String comment,
        LocalDateTime ratingDate
) {}