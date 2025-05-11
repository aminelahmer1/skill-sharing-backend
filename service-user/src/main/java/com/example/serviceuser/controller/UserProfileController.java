package com.example.serviceuser.controller;

import com.example.serviceuser.dto.UserResponse;
import com.example.serviceuser.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200", allowCredentials = "true")
public class UserProfileController {

    private final UserService userService;

    @GetMapping("/id/{userId}")
    public ResponseEntity<UserResponse> getUserProfile(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.findById(userId));
    }
    @GetMapping("/by-keycloak-id")
    public ResponseEntity<UserResponse> getUserByKeycloakId(
            @RequestParam String keycloakId) {
        return ResponseEntity.ok(userService.findByKeycloakId(keycloakId));
}}
