package com.example.serviceskill.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record SkillRequest(
        Integer id,
        @NotNull(message = "Skill name is required")
        @NotBlank(message = "Skill name cannot be empty")
        @Size(min = 1, max = 100, message = "Skill name must be between 1 and 100 characters")
        String name,

        @NotNull(message = "Skill description is required")
        @NotBlank(message = "Skill description cannot be empty")
        @Size(min = 10, max = 500, message = "Description must be between 10 and 500 characters")
        String description,

        @NotNull(message = "Available quantity is required")
        @Min(value = 1, message = "Available quantity must be at least 1")
        @Max(value = 100, message = "Available quantity cannot exceed 100")
        Integer availableQuantity,
        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.0", inclusive = true, message = "Price must be 0 or positive")
        @DecimalMax(value = "10000.0", message = "Price cannot exceed 10000")
        @Digits(integer = 8, fraction = 2, message = "Price must have maximum 2 decimal places")
        BigDecimal price,

        @NotNull(message = "Skill category is required") Integer categoryId,
        String pictureUrl,
        @NotNull String streamingDate,
        @NotNull   String streamingTime
) {}