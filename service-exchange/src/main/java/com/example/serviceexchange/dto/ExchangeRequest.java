package com.example.serviceexchange.dto;

import jakarta.validation.constraints.NotNull;

public record ExchangeRequest(
        @NotNull(message = "Provider ID is required")
        Long providerId,
        @NotNull(message = "Receiver ID is required")
        Long receiverId,
        @NotNull(message = "Skill ID is required")
        Integer skillId
) {}