package com.example.serviceuser.dto;

import com.example.serviceuser.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

public record UserRequest(
        Long id,
        @NotNull(message = "Username is required")
        String username,
        @NotNull(message = "Email is required")
        @Email(message = "Email is not valid")
        String email,
        String city,
        String governorate,
        String role
) {}