package com.example.serviceexchange.dto;

import java.util.Map;

public record UserSkillsStats(
        int totalSkills,
        int totalUsers, // tous les utilisateurs uniques across all skills
        int totalProducers, // nombre de producteurs uniques
        int totalReceivers, // nombre de receivers uniques
        Map<String, Integer> statusBreakdown // répartition des statuts
) {}