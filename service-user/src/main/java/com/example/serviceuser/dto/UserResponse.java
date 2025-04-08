package com.example.serviceuser.dto;

import com.example.serviceuser.entity.Address;
import com.example.serviceuser.entity.User;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
public record UserResponse(
        Long id,
        String keycloakId,
        String username,
        String email,
        String firstName,
        String lastName,
        Address address, // Modifié pour utiliser Address
        List<String> roles,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}