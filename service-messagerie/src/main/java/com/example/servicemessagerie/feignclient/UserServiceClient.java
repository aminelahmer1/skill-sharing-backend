package com.example.servicemessagerie.feignclient;

import com.example.servicemessagerie.dto.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "service-user", url = "${application.config.user-url}")
public interface UserServiceClient {

    @GetMapping("/{userId}")
    ResponseEntity<UserResponse> getUserById(
            @PathVariable Long userId,
            @RequestHeader("Authorization") String token
    );

    @GetMapping("/by-keycloak-id")
    ResponseEntity<UserResponse> getUserByKeycloakId(
            @RequestParam String keycloakId,
            @RequestHeader("Authorization") String token
    );
}