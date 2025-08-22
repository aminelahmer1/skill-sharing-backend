package com.example.serviceexchange.dto;

import java.util.List;

public record SubscriberDetailResponse(
        Long userId,
        String keycloakId,
        String username,
        String email,
        String firstName,
        String lastName,
        String pictureUrl,
        List<String> roles,
        List<SkillSubscriptionInfo> subscribedSkills
) {}