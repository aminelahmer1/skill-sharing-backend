package com.example.notification.client;

import com.example.notification.dto.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "service-user", url = "http://localhost:8822")
public interface UserServiceClient {

    @GetMapping("/api/v1/users/{id}")
    UserResponse getUserById(@PathVariable("id") Long id, @RequestHeader("Authorization") String token);

    @GetMapping("/api/v1/users/keycloak/{keycloakId}")
    UserResponse getUserByKeycloakId(@PathVariable("keycloakId") String keycloakId, @RequestHeader("Authorization") String token);
}