package com.example.serviceuser.service;

import com.example.serviceuser.dto.UserRequest;
import com.example.serviceuser.dto.UserResponse;
import com.example.serviceuser.entity.Role;
import com.example.serviceuser.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public User toUser(UserRequest request) {
        if (request == null) {
            return null;
        }
        return User.builder()
                .id(request.id())
                .username(request.username())
                .email(request.email())
                .city(request.city())
                .governorate(request.governorate())
                .role(Role.valueOf(request.role()))
                .build();
    }

    public UserResponse fromUser(User user) {
        if (user == null) {
            return null;
        }
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getCity(),
                user.getGovernorate(),
                user.getRole()
        );
    }
}