package com.example.serviceexchange.dto;

import java.util.List;

public record ProducerQualityStats(
        double averageResponseTimeHours,
        double satisfactionRate,
        int totalRatings,
        double averageRating,
        List<RatingDistribution> ratingDistribution,
        String qualityTrend // "IMPROVING", "STABLE", "DECLINING"
) {}