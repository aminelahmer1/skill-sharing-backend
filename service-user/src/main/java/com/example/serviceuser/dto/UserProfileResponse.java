package com.example.serviceuser.dto;

import java.time.LocalDateTime;
import java.util.List;

public record UserProfileResponse(
        Long id,
        String keycloakId,
        String username,
        String email,
        String firstName,
        String lastName,
        AddressResponse address,
        String pictureUrl,
        String phoneNumber,
        List<String> roles,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}