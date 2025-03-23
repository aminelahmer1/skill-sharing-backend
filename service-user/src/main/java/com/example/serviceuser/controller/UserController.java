package com.example.serviceuser.controller;

import com.example.serviceuser.dto.UserRequest;
import com.example.serviceuser.dto.UserResponse;
import com.example.serviceuser.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<String> createUser(@RequestBody @Valid UserRequest request) {
        return ResponseEntity.ok(userService.createUser(request));
    }

    @PutMapping
    public ResponseEntity<Void> updateUser(@RequestBody @Valid UserRequest request) {
        userService.updateUser(request);
        return ResponseEntity.accepted().build();
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> findAll() {
        return ResponseEntity.ok(userService.findAllUsers());
    }

    @GetMapping("/exists/{user-id}")
    public ResponseEntity<Boolean> existsById(@PathVariable("user-id") Long userId) {
        return ResponseEntity.ok(userService.existsById(userId));
    }

    @GetMapping("/{user-id}")
    public ResponseEntity<UserResponse> findById(@PathVariable("user-id") Long userId) {
        return ResponseEntity.ok(userService.findById(userId));
    }

    @DeleteMapping("/{user-id}")
    public ResponseEntity<Void> delete(@PathVariable("user-id") Long userId) {
        userService.deleteUser(userId);
        return ResponseEntity.accepted().build();
    }
}