package com.example.serviceuser.dto;

import com.example.serviceuser.entity.Role;
import jakarta.persistence.Column;

import java.util.Set;

public record UserDTO(
        Long id,
        String username,
        String email,
        String password,
        String city,

        String governorate,
        Role role



) {}