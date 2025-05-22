package com.example.serviceexchange.dto;




import java.time.LocalDateTime;
import java.util.List;

public record UserResponse(
        Long id,
        String keycloakId,
        String username,
        String email,
        String firstName,
        String lastName,
        String city,
        String country,
        String postalCode,
        List<String> roles,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String pictureUrl,
        String bio,
        String phoneNumber
) {
   }

