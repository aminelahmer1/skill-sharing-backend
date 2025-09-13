package com.example.serviceexchange.dto;

import java.util.List;

public record ProducerDashboardStats(
        // Métriques principales
        int upcomingSessions,
        int totalSkills,
        double averageRating,
        int totalStudents,

        // Performance
        double completionRate,
        double rebookingRate,
        double satisfactionRate,
        double averageResponseTimeHours,

        // Croissance
        int sessionsThisMonth,
        int sessionsLastMonth,
        double monthlyGrowthRate,
        int newStudentsThisMonth,
        int totalTeachingHours,

        // Comparaison
        int platformRanking,
        double platformAverageRating,

        // Données pour graphiques
        List<MonthlyActivityData> monthlyActivity,
        List<SkillPerformanceData> skillPerformance,
        List<RatingEvolutionData> ratingEvolution
) {}