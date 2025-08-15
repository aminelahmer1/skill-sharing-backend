package com.example.serviceexchange.dto;

import java.util.List;

public record SkillRatingStats(
        Integer skillId,
        String skillName,
        Double averageRating,
        Integer totalRatings,
        List<RatingResponse> ratings
) {}