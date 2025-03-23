package com.example.serviceexchange.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "exchange")
public class Exchange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "provider_id", nullable = false)
    private Long providerId; // ID du Provider

    @Column(name = "receiver_id", nullable = false)
    private Long receiverId; // ID du Receiver

    @Column(name = "skill_id", nullable = false)
    private Integer skillId; // ID de la compétence échangée

    @Column(nullable = false)
    private String status; // Statut de l'échange (PENDING, ACCEPTED, REJECTED, COMPLETED)

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt; // Date de création de l'échange

    @Column(name = "provider_rating")
    private Integer providerRating; // Note donnée par le Receiver au Provider
}