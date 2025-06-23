package com.example.serviceexchange.dto;

public record NotificationEvent(
        String type,
        Integer exchangeId,
        Long producerId,
        Long receiverId,
        String skillName,
        String reason,
        String streamingDate

) {
}