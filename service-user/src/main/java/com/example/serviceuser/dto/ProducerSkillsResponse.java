package com.example.serviceuser.dto;

import java.util.List;

public record ProducerSkillsResponse(
        Long userId,
        String username,
        List<SkillResponse> skills
) {}