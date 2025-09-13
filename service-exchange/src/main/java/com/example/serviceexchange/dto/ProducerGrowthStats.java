package com.example.serviceexchange.dto;

public record ProducerGrowthStats(
        int sessionsThisMonth,
        int sessionsLastMonth,
        double monthlyGrowthRate,
        int newStudentsThisMonth,
        int totalTeachingHours,
        double yearOverYearGrowth
) {}