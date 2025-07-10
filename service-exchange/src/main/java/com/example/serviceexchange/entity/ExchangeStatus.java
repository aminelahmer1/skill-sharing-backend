package com.example.serviceexchange.entity;

import java.util.Set;

public enum ExchangeStatus {
    PENDING, ACCEPTED, SCHEDULED, IN_PROGRESS, COMPLETED, REJECTED, CANCELLED;

    private static final Set<String> VALID_STATUSES = Set.of(
            PENDING.name(), ACCEPTED.name(), SCHEDULED.name(), IN_PROGRESS.name(),
            COMPLETED.name(), REJECTED.name(), CANCELLED.name()
    );

    public static boolean isValid(String status) {
        return VALID_STATUSES.contains(status);
    }
}