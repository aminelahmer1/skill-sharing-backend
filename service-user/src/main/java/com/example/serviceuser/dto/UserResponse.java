package com.example.serviceuser.dto;

public record UserResponse(
        Long id,
        String username,
        String email,
        String role
) {}