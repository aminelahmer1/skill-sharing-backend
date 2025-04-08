package com.example.serviceexchange.entity;

import java.util.Set;

public class ExchangeStatus {
    public static final String PENDING = "PENDING";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String REJECTED = "REJECTED";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";

    private static final Set<String> VALID_STATUSES = Set.of(
            PENDING, ACCEPTED, REJECTED, COMPLETED, CANCELLED
    );

    public static boolean isValid(String status) {
        return VALID_STATUSES.contains(status);
    }

    // Ajoutez cette méthode pour valider et lever une exception si invalide
    public static void validate(String status) {
        if (!isValid(status)) {
            throw new IllegalArgumentException("Invalid exchange status: " + status);
        }
    }
}