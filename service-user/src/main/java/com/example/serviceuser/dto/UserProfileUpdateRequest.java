package com.example.serviceuser.dto;

public record UserProfileUpdateRequest(
        String firstName,       // Optional
        String lastName,        // Optional
        String phoneNumber,     // Optional
        String pictureUrl,      // Optional
        AddressUpdateRequest address  // Optional
) {}