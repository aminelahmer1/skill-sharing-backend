package com.example.serviceuser.dto;

import java.math.BigDecimal;

public record SkillResponse(
        Integer id,
        String name,
        String description,
        Integer availableQuantity,
        BigDecimal price,
        Integer nbInscrits,
        Integer categoryId,
        String categoryName,
        String categoryDescription,
        Long userId
) {}