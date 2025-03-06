package com.example.serviceskill.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@Entity
@Table(name = "skill", schema = "public") // Nom de la table et schéma
public class Skill {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Auto-incrémentation pour PostgreSQL
    private Integer id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(name = "available_quantity", nullable = false)
    private double availableQuantity;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(name = "nb_inscrits", nullable = false)
    private int nbInscrits; // Nouvel attribut

    @ManyToOne
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;
}