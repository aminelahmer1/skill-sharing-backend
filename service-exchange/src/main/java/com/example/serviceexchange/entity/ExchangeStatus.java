package com.example.serviceexchange.entity;

import java.util.Set;

public class ExchangeStatus {
    public static final String PENDING = "PENDING"; // Demande initiale
    public static final String ACCEPTED = "ACCEPTED"; // Accepté par le producteur
    public static final String WAITING = "WAITING"; // En attente de la session
    public static final String IN_PROGRESS = "IN_PROGRESS"; // Session en cours
    public static final String COMPLETED = "COMPLETED"; // Session terminée
    public static final String REJECTED = "REJECTED"; // Refusé par le producteur
    public static final String CANCELLED = "CANCELLED"; // Annulé

    private static final Set<String> VALID_STATUSES = Set.of(
            PENDING, ACCEPTED, WAITING, IN_PROGRESS, COMPLETED, REJECTED, CANCELLED
    );

    public static boolean isValid(String status) {
        return VALID_STATUSES.contains(status);
    }
}