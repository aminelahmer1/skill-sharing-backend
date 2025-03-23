package com.example.serviceexchange.dto;

public record UserResponse(
        Long id,
        String username,
        String email,
        String role
) {}