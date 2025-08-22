package com.example.serviceexchange.dto;

import java.util.List;

public record UserSkillsWithUsersResponse(
        UserResponse currentUser,
        String userPrimaryRole, // "PRODUCER" ou "RECEIVER"
        List<SkillWithUsersResponse> skills,
        UserSkillsStats globalStats
) {}
