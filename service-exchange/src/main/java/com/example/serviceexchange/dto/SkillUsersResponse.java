package com.example.serviceexchange.dto;

import java.util.List;

public record SkillUsersResponse(
        Integer skillId,
        String skillName,
        String skillDescription,
        UserResponse skillProducer,
        List<UserResponse> receivers,
        SkillUsersStats stats,
        String currentUserRole // "PRODUCER" ou "RECEIVER"
) {}