package com.example.serviceuser.dto;

import brave.internal.Nullable;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record KeycloakProfileUpdateRequest(
        @Nullable String username,
        @Nullable String firstName,
        @Nullable String lastName,
        @Nullable String phoneNumber
) {}