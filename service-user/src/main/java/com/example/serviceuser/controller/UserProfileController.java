package com.example.serviceuser.controller;

import com.example.serviceuser.dto.UserResponse;
import com.example.serviceuser.entity.User;
import com.example.serviceuser.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@CrossOrigin(origins = "http://localhost:4200", allowedHeaders = "*", allowCredentials = "true")
public class UserProfileController {

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserResponse response = new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),

                user.getGovernorate(),
                user.getCity(),
                user.getRole()
        );

        return ResponseEntity.ok(response);
    }

    // Autres endpoints pour la gestion des profils utilisateur
}