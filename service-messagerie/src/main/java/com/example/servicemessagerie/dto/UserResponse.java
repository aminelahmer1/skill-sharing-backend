package com.example.servicemessagerie.dto;


public record UserResponse(
        Long id,
        String username,
        String firstName,
        String lastName,
        String email,
        String profileImageUrl
) {}