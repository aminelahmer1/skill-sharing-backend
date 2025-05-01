package com.example.serviceskill.dto;


import com.example.serviceuser.entity.User;

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
    public UserResponse(User user) {
        this(
                user.getId(),
                user.getKeycloakId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getAddress() != null ? user.getAddress().getCity() : null,
                user.getAddress() != null ? user.getAddress().getCountry() : null,
                user.getAddress() != null ? user.getAddress().getPostalCode() : null,
                user.getRoles(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getPictureUrl(),
                user.getBio(),
                user.getPhoneNumber()
        );
    }

}