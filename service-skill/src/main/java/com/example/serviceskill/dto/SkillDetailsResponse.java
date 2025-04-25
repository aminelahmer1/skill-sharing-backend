package com.example.serviceskill.dto;

import java.math.BigDecimal;

public record SkillDetailsResponse(
        Long id,
        String name,
        String description,
        Integer availableQuantity,
        Integer currentRegistrations,
        BigDecimal price,
        String categoryName,
        String producerName
) {}