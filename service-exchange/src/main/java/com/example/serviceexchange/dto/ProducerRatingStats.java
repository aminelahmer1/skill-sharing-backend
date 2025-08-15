package com.example.serviceexchange.dto;

import java.util.List;

public record ProducerRatingStats(
        Long producerId,
        String producerName,
        Double averageRating,
        Integer totalRatings,
        Integer totalExchanges,
        List<RatingDistribution> ratingDistribution,
        List<RecentRating> recentRatings
) {}