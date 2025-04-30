package com.example.serviceuser.dto;

public record UserProfileUpdateRequest(
        String username,      // Ajouté pour Keycloak
        String firstName,     // Synchronisé avec Keycloak
        String lastName,      // Synchronisé avec Keycloak
        String phoneNumber,   // Reste local
        AddressUpdateRequest address,  // Reste local
        String pictureUrl   ,  // Reste local
        String bio
) {}