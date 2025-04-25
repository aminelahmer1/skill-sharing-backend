package com.example.serviceuser.dto;

import com.example.serviceuser.entity.Address;
import java.time.LocalDateTime;
import java.util.List;

public record UserResponse(
        Long id,
        String keycloakId,
        String username,
        String email,
        String firstName,
        String lastName,
        Address address,
        List<String> roles,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String pictureUrl,
        String phoneNumber
) {}
