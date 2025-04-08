package com.example.serviceskill.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record SkillRequest(
        Integer id,
        @NotNull(message = "Skill name is required")
        String name,
        @NotNull(message = "Skill description is required")
        String description,
        @Positive(message = "Available quantity should be positive")
        Integer availableQuantity,
        @Positive(message = "Price should be positive")
        BigDecimal price,
        @NotNull(message = "Skill category is required")
        Integer categoryId,
        @NotNull(message = "User ID is required")
        Long userId
) {}