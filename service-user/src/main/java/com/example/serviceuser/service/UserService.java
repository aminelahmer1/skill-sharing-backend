package com.example.serviceuser.service;

import com.example.serviceuser.configuration.KeycloakAdminService;
import com.example.serviceuser.dto.UserResponse;
import com.example.serviceuser.entity.User;
import com.example.serviceuser.exception.UserNotFoundException;
import com.example.serviceuser.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final KeycloakAdminService keycloakAdminService;
    private final UserMapper userMapper;

    /**
     * Synchronize all users from Keycloak to the PostgreSQL database.
     */
    public void syncUsersWithKeycloak() {
        log.info("Starting user synchronization with Keycloak...");

        // Fetch all Keycloak users
        List<UserRepresentation> keycloakUsers = keycloakAdminService.getAllUsers();

        // Iterate through users and synchronize
        for (UserRepresentation kcUser : keycloakUsers) {
            synchronizeUser(kcUser);
        }

        log.info("User synchronization with Keycloak completed successfully.");
    }

    /**
     * Synchronize a single Keycloak user with the database.
     *
     * @param kcUser User representation from Keycloak.
     */
    private void synchronizeUser(UserRepresentation kcUser) {
        log.info("Synchronizing Keycloak user with ID: {}", kcUser.getId());

        userRepository.findByKeycloakId(kcUser.getId())
                .ifPresentOrElse(
                        existingUser -> {
                            log.info("Updating existing user: {}", existingUser.getKeycloakId());
                            userMapper.updateFromKeycloak(existingUser, kcUser);
                            userRepository.save(existingUser);
                        },
                        () -> {
                            log.info("Creating new user with Keycloak ID: {}", kcUser.getId());
                            User newUser = new User();
                            userMapper.updateFromKeycloak(newUser, kcUser);
                            newUser.setKeycloakId(kcUser.getId());
                            userRepository.save(newUser);
                        }
                );
    }

    /**
     * Find a user by their Keycloak ID and map it to UserResponse.
     *
     * @param keycloakId The Keycloak ID of the user.
     * @return UserResponse containing user details.
     */
    public UserResponse findByKeycloakId(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new UserNotFoundException("User not found with Keycloak ID: " + keycloakId));
    }

    /**
     * Find a user by their internal database ID.
     *
     * @param userId The ID of the user in the database.
     * @return UserResponse containing user details.
     */
    public UserResponse findById(Long userId) {
        return userRepository.findById(userId)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + userId));
    }
}