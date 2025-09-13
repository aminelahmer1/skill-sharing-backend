package com.example.serviceexchange.dto;

public record RatingEvolutionData(
        int year,
        int month,
        String monthLabel,
        double averageRating
) {}