package com.example.serviceskill.dto;

import java.math.BigDecimal;

public record SkillResponse(
        Integer id,
        String name,
        String description,
        double availableQuantity,
        BigDecimal price,
        int nbInscrits,
        Integer categoryId,
        String categoryName,
        String categoryDescription,
        Long userId // ID de l'utilisateur (PROVIDER)
) {}
