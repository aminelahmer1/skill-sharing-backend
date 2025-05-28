package com.example.serviceuser.client;

import com.example.serviceuser.dto.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "service-user")
public interface UserServiceClient {
    @GetMapping("/api/v1/users/{id}")
    UserResponse getUserById(@PathVariable("id") Long id, @RequestHeader("Authorization") String token);

    @GetMapping("/api/v1/users/keycloak/{keycloakId}")
    UserResponse getUserByKeycloakId(@PathVariable("keycloakId") String keycloakId, @RequestHeader("Authorization") String token);
}