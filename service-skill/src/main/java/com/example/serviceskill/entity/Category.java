package com.example.serviceskill.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@Entity
@Table(name = "category", schema = "public") // Nom de la table et schéma
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Auto-incrémentation pour PostgreSQL
    private Integer id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @OneToMany(mappedBy = "category", cascade = CascadeType.REMOVE)
    private List<Skill> skills;
}