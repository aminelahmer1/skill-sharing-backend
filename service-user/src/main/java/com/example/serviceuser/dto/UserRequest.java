package com.example.serviceuser.dto;



import java.util.List;

public record UserRequest(
        Long id,
        String keycloakId,
        String username,
        String email,
        List<String> roles,
        String city,
        String governorate
) {}