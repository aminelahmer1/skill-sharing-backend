package com.example.servicemessagerie.dto;

public record ExchangeResponse(
        Integer id,
        Integer skillId,
        Long producerId,
        Long receiverId,
        String status
) {}