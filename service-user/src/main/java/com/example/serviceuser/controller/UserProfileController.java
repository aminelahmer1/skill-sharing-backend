package com.example.serviceuser.controller;

import com.example.serviceuser.dto.UserResponse;
import com.example.serviceuser.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserService userService;

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserProfile(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.findById(userId));
    }
}
