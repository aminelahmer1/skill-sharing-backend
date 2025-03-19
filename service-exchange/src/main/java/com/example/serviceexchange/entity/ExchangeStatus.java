package com.example.serviceexchange.entity;


public enum ExchangeStatus {
    PENDING,   // En attente de confirmation
    ACCEPTED,  // Échange accepté par le provider
    COMPLETED, // Échange terminé
    CANCELLED  // Échange annulé
}