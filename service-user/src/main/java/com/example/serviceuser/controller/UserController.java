package com.example.serviceuser.controller;

import com.example.serviceuser.dto.UserResponse;
import com.example.serviceuser.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    /**
     * API for manual synchronization with Keycloak.
     */
    @PostMapping("/sync")
    public ResponseEntity<String> syncUsers() {
        log.info("Manual synchronization request received...");
        userService.syncUsersWithKeycloak();
        return ResponseEntity.ok("User synchronization completed successfully.");
    }

    /**
     * Get a user by their Keycloak ID.
     */
    @GetMapping
    public ResponseEntity<UserResponse> getUserByKeycloakId(@RequestParam(required = false) String keycloakId) {
        if (keycloakId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(userService.findByKeycloakId(keycloakId));
    }

    @GetMapping("/all")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        log.info("Request received to fetch all users...");
        List<UserResponse> users = userService.findAllUsers();
        return ResponseEntity.ok(users);
    }


}
