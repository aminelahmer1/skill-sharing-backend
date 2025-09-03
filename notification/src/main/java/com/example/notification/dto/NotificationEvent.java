package com.example.notification.dto;

public record NotificationEvent(
        String type,
        Integer exchangeId,
        Long producerId,
        Long receiverId,
        String skillName,
        String reason,
        String streamingDate
) {}