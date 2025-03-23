package com.example.serviceexchange.FeignClient;

import com.example.serviceexchange.dto.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "service-user", url = "${application.config.user-url}")
public interface UserServiceClient {

    @GetMapping("/{user-id}")
    UserResponse getUserById(@PathVariable("user-id") Long userId);
}