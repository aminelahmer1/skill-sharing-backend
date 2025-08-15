package com.example.serviceexchange.dto;

public record RatingDistribution(
        Integer stars,
        Integer count,
        Double percentage
) {}