package com.example.serviceexchange.dto;

import java.time.LocalDateTime;

public record SkillSubscriptionInfo(
        Integer skillId,
        String skillName,
        String status,
        LocalDateTime exchangeDate
) {}