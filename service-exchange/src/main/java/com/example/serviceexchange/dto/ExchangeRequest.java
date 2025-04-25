package com.example.serviceexchange.dto;

import jakarta.validation.constraints.NotNull;

public record ExchangeRequest(
        @NotNull(message = "Producer ID is required")
        Long producerId,
        @NotNull(message = "Receiver ID is required")
        Long receiverId,
        @NotNull(message = "Skill ID is required")
        Integer skillId
) {}