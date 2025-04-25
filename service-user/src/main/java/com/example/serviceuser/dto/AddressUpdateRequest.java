package com.example.serviceuser.dto;

public record AddressUpdateRequest(
        String city,
        String country,
        String postalCode
) {}