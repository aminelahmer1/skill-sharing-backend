package com.example.serviceexchange.dto;

import java.util.List;

public record SkillWithUsersResponse(
        Integer skillId,
        String skillName,
        String skillDescription,
        UserResponse skillProducer,
        List<UserResponse> receivers,
        SkillUsersStats stats,
        String userRole // "PRODUCER" ou "RECEIVER"
) {}

