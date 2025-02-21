package com.example.serviceuser.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;


import java.time.LocalDateTime;

@Entity
public class InvalidToken {

    @Id
    private String token;
    private LocalDateTime expirationDate;

    // Getters et Setters
    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public LocalDateTime getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(LocalDateTime expirationDate) {
        this.expirationDate = expirationDate;
    }
}