package com.example.serviceexchange.dto;

public record MonthlyActivityData(
        int year,
        int month,
        String monthLabel,
        int completedSessions,
        int upcomingSessions
) {}