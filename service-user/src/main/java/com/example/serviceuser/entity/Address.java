package com.example.serviceuser.entity;

import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Address {
    private String street;
    private String locality; // Ville
    private String region;   // Gouvernorat/État/Province
    private String postalCode;
    private String country;
}