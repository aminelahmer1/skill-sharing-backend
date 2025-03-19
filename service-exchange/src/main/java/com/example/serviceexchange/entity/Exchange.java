package com.example.serviceexchange.entity;


import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "exchange", schema = "public")
public class Exchange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long providerId; // ID de l'utilisateur qui propose la compétence

    @Column(nullable = false)
    private Long receiverId; // ID de l'utilisateur qui reçoit la compétence

    @Column(nullable = false)
    private Long skillId; // ID de la compétence échangée

    @Column(nullable = false)
    private LocalDateTime exchangeDate; // Date de l'échange

    @Enumerated(EnumType.STRING) // Stocke le statut sous forme de chaîne de caractères dans la base de données
    @Column(nullable = false)
    private ExchangeStatus status; // Statut de l'échange (PENDING, ACCEPTED, COMPLETED, CANCELLED)
}
