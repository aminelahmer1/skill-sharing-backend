package com.example.serviceuser.dto;

public record UserResponse(
        Long id,
        String username,
        String email,
        String city,
        String governorate,
        com.example.serviceuser.entity.Role role
) {}