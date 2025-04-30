package com.example.serviceuser.dto;

public record AddressResponse(
        String city,
        String country,
        String postalCode
) {}