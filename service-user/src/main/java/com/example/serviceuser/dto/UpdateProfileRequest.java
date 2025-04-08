package com.example.serviceuser.dto;

import com.example.serviceuser.entity.Address;
import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequest(
        Address address
) {}