package com.example.serviceuser.dto;

public record UserCreateRequest(
        String username,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        String role,
        String password

) {}
