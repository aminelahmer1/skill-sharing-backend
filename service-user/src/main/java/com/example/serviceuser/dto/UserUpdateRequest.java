package com.example.serviceuser.dto;

public record UserUpdateRequest(
        String phoneNumber,
        String city,
        String country,
        String postalCode,
        String pictureUrl
) {}