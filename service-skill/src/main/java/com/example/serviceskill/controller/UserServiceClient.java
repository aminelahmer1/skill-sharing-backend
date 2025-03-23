package com.example.serviceskill.controller;

import com.example.serviceskill.configuration.FeignConfig;

import com.example.serviceskill.dto.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
@FeignClient(
        name = "service-user",
        url = "${application.config.User-url}"
)
public interface UserServiceClient {

    @GetMapping("/{userId}")
    UserResponse getUserById(@PathVariable Long userId);
}