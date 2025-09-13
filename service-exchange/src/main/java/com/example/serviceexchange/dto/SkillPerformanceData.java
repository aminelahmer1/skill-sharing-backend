package com.example.serviceexchange.dto;

public record SkillPerformanceData(
        int skillId,
        String skillName,
        double averageRating,
        int totalSessions,
        int pendingRequests,
        boolean isTopPerforming
) {}