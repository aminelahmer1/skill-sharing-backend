package com.example.serviceexchange.dto;

public record CommunityStats(
        int totalSkills,
        int totalProducers,
        int totalOtherReceivers,
        int totalMembers
) {}