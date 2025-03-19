package com.example.serviceskill.dto;

public record UserResponse(
        Long id,
        String username,
        String email,
        String role
) {}