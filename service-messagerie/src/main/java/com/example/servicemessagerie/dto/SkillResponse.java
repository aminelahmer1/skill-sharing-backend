package com.example.servicemessagerie.dto;

public record SkillResponse(
        Integer id,
        String name,
        String description,
        Long userId,
        String category
) {}
