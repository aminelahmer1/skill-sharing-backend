package com.example.serviceuser.entity;

import com.example.serviceuser.entity.Role;
import jakarta.persistence.*;
import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Entity
@Data
@Table(name = "app_user") // Définir le nom correct de la table
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false) // Supprimer l'unicité sur le username
    private String username;

    @Column(nullable = false, unique = true) // Email unique
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role; // ROLE_PROVIDER ou ROLE_RECEIVER

    @ElementCollection(fetch = FetchType.EAGER)
    private Set<String> providedSkills = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    private Set<String> neededSkills = new HashSet<>();
}
