package com.example.serviceuser.dto;

import com.example.serviceuser.entity.Role;
import java.util.Set;

public record ProfileResponseDTO(
        String username,
        String email,
        Role role,
        Set<String> skills,
        Set<String> skillsNeeded
) {}