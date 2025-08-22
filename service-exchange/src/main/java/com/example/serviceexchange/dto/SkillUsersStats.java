package com.example.serviceexchange.dto;

import java.util.Map;

public record SkillUsersStats(
        int totalReceivers,
        int totalUsers, // producer + receivers
        Map<String, Integer> statusBreakdown // nombre par statut
) {}