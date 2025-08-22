package com.example.serviceexchange.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SkillCommunityResponse(
        Integer skillId,
        String skillName,
        String skillDescription,
        UserResponse producer,
        List<UserResponse> otherReceivers,
        LocalDateTime myExchangeDate,
        String myExchangeStatus
) {}