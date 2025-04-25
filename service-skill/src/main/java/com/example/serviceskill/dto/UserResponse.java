package com.example.serviceskill.dto;

import com.example.serviceuser.entity.Address;

import java.time.LocalDateTime;
import java.util.List;

public record UserResponse(
        Long userId,
        String keycloakId,
        String username,
        String email,
        String firstName,
        String lastName,
        Address address,
        List<String> roles,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}