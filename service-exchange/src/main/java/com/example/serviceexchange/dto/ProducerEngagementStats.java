package com.example.serviceexchange.dto;

public record ProducerEngagementStats(
        double completionRate,
        double averageSessionDurationHours,
        double rebookingRate,
        int uniqueStudents,
        int totalInteractions,
        double studentRetentionRate
) {}