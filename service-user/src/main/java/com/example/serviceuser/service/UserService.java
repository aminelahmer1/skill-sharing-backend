package com.example.serviceuser.service;

import com.example.serviceuser.dto.UserRequest;
import com.example.serviceuser.dto.UserResponse;
import com.example.serviceuser.entity.User;
import com.example.serviceuser.exception.UserNotFoundException;

import com.example.serviceuser.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public String createUser(UserRequest request) {
        User user = userMapper.toUser(request);
        userRepository.save(user);
        return user.getId().toString();
    }

    public void updateUser(UserRequest request) {
        User user = userRepository.findById(request.id())
                .orElseThrow(() -> new UserNotFoundException(
                        String.format("Cannot update user: No user found with the provided ID: %s", request.id())
                ));
        mergeUser(user, request);
        userRepository.save(user);
    }

    private void mergeUser(User user, UserRequest request) {
        if (StringUtils.isNotBlank(request.username())) {
            user.setUsername(request.username());
        }
        if (StringUtils.isNotBlank(request.email())) {
            user.setEmail(request.email());
        }
        if (StringUtils.isNotBlank(request.city())) {
            user.setCity(request.city());
        }
        if (StringUtils.isNotBlank(request.governorate())) {
            user.setGovernorate(request.governorate());
        }
    }

    public List<UserResponse> findAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(userMapper::fromUser)
                .collect(Collectors.toList());
    }

    public UserResponse findById(Long id) {
        return userRepository.findById(id)
                .map(userMapper::fromUser)
                .orElseThrow(() -> new UserNotFoundException(String.format("No user found with the provided ID: %s", id)));
    }

    public boolean existsById(Long id) {
        return userRepository.findById(id).isPresent();
    }

    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }
}