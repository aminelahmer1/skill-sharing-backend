package com.example.serviceuser.dto;

import com.example.serviceuser.entity.Role;

public record UserDTO(
        Long id,
        String username,
        String email,
        String password,
        Role role
) {}