package com.example.serviceuser.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.util.Set;

@Entity
@Data
@Table(name = "app_user")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;


    @Column(name = "city")
    private String city;

    @Column(nullable = false)
    private String governorate;



    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;
}