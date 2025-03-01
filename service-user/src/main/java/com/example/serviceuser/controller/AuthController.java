package com.example.serviceuser.controller;

import com.example.serviceuser.dto.UserDTO;
import com.example.serviceuser.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "http://localhost:4200", allowedHeaders = "*", allowCredentials = "true")
public class AuthController {

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody UserDTO userDTO) {
        Map<String, String> response = new HashMap<>();
        try {
            String result = userService.registerUser(userDTO);
            response.put("message", result);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            response.put("message", "Registration failed");
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody Map<String, String> request) {
        Map<String, String> response = new HashMap<>();
        try {
            String token = userService.loginUser(request.get("email"), request.get("password"));
            response.put("message", "Login successful");
            response.put("token", token);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            response.put("message", "Login failed");
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestHeader("Authorization") String token) {
        Map<String, String> response = new HashMap<>();
        try {
            userService.logout(token.replace("Bearer ", ""));
            response.put("message", "Logout successful!");
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            response.put("message", "Logout failed");
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}