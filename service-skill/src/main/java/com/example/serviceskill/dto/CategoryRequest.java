package com.example.serviceskill.dto;

import jakarta.validation.constraints.NotNull;

public record CategoryRequest(
        Integer id,
        @NotNull(message = "Category name is required")
        String name,
        @NotNull(message = "Category description is required")
        String description
) {}