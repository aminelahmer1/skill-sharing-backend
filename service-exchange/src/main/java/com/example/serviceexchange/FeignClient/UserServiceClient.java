package com.example.serviceexchange.FeignClient;


import com.example.serviceuser.dto.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "service-user", url = "http://localhost:8081")
public interface UserServiceClient {
    @GetMapping("/users/{userId}")
    UserResponse getUserById(@PathVariable Long userId);
}