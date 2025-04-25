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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final KeycloakAdminService keycloakAdminService;
    private final UserMapper userMapper;

    /**
     * Synchronize users with Keycloak, handle deletions, and update roles.
     */
    public void syncUsersWithKeycloak() {
        log.info("Starting user synchronization with Keycloak...");

        // Fetch all users from Keycloak
        List<UserRepresentation> keycloakUsers = keycloakAdminService.getAllUsers();
        List<String> keycloakIds = keycloakUsers.stream()
                .map(UserRepresentation::getId)
                .collect(Collectors.toList());

        // Fetch all users from the database
        List<User> databaseUsers = userRepository.findAll();

        // Detect and delete users no longer in Keycloak
        List<User> usersToDelete = databaseUsers.stream()
                .filter(user -> !keycloakIds.contains(user.getKeycloakId()))
                .collect(Collectors.toList());

        usersToDelete.forEach(user -> {
            log.info("Deleting user from database: {}", user.getUsername());
            userRepository.delete(user);
        });

        // Synchronize Keycloak users with the database
        keycloakUsers.forEach(kcUser -> {
            String roleAttribute = keycloakAdminService.getUserRoleAttribute(kcUser);
            List<String> roles = keycloakAdminService.getUserRoles(kcUser.getId());

            // Assign realm role if needed
            if (roleAttribute != null && !roles.contains(roleAttribute)) {
                keycloakAdminService.assignRealmRoleToUser(kcUser.getId(), roleAttribute);
            }

            synchronizeUser(kcUser, roles); // Synchronize user roles
        });

        log.info("User synchronization with Keycloak completed successfully.");
    }

    /**
     * Synchronize a single user with the local database.
     */
    private void synchronizeUser(UserRepresentation kcUser, List<String> roles) {
        String userId = kcUser.getId();

        // Create or update user in the database
        userRepository.findByKeycloakId(userId)
                .ifPresentOrElse(
                        existingUser -> {
                            log.info("Updating existing user: {}", existingUser.getUsername());
                            userMapper.updateFromKeycloak(existingUser, kcUser);
                            existingUser.setRoles(roles); // Update roles
                            userRepository.save(existingUser);
                        },
                        () -> {
                            log.info("Creating new user with Keycloak ID: {}", userId);
                            User newUser = new User();
                            userMapper.updateFromKeycloak(newUser, kcUser);
                            newUser.setKeycloakId(userId);
                            newUser.setRoles(roles); // Assign roles
                            userRepository.save(newUser);
                        }
                );
    }

    /**
     * Find a user by their Keycloak ID.
     */
    public UserResponse findByKeycloakId(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new UserNotFoundException("User not found with Keycloak ID: " + keycloakId));
    }

    /**
     * Find a user by their database ID.
     */
    public UserResponse findById(Long userId) {
        return userRepository.findById(userId)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + userId));
    }

    /**
     * Fetch all users from the database.
     */
    public List<UserResponse> findAllUsers() {
        log.info("Fetching all users from the database...");
        return userRepository.findAll().stream()
                .map(userMapper::toResponse)
                .collect(Collectors.toList());
    }
    public void syncSingleUser(String userId) {
        log.info("Synchronizing user with Keycloak ID: {}", userId);

        UserRepresentation kcUser = keycloakAdminService.getUserById(userId);
        String roleAttribute = keycloakAdminService.getUserRoleAttribute(kcUser);
        List<String> roles = keycloakAdminService.getUserRoles(userId);

        if (roleAttribute != null && !roles.contains(roleAttribute)) {
            keycloakAdminService.assignRealmRoleToUser(userId, roleAttribute);
        }

        userRepository.findByKeycloakId(userId)
                .ifPresentOrElse(
                        existingUser -> {
                            log.info("Updating existing user: {}", existingUser.getUsername());
                            userMapper.updateFromKeycloak(existingUser, kcUser);
                            existingUser.setRoles(roles);
                            userRepository.save(existingUser);
                        },
                        () -> {
                            log.info("Creating new user with Keycloak ID: {}", userId);
                            User newUser = new User();
                            userMapper.updateFromKeycloak(newUser, kcUser);
                            newUser.setKeycloakId(userId);
                            newUser.setRoles(roles);
                            userRepository.save(newUser);
                        }
                );
    }
}
