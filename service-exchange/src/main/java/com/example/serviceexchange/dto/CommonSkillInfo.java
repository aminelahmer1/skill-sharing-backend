package com.example.serviceexchange.dto;

import java.time.LocalDateTime;

public record CommonSkillInfo(
        Integer skillId,
        String skillName,
        String producerName,
        String status,
        LocalDateTime exchangeDate
) {}