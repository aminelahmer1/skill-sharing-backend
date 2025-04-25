package com.example.serviceuser.service;

import com.example.serviceuser.configuration.KeycloakAdminService;
import com.example.serviceuser.dto.AddressUpdateRequest;
import com.example.serviceuser.dto.UserProfileUpdateRequest;
import com.example.serviceuser.dto.UserResponse;
import com.example.serviceuser.entity.Address;
import com.example.serviceuser.entity.User;
import com.example.serviceuser.exception.UserNotFoundException;
import com.example.serviceuser.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private static final String PHONE_ATTRIBUTE = "phone-mapper";
    private static final String CITY_ATTRIBUTE = "city";
    private static final String COUNTRY_ATTRIBUTE = "country";
    private static final String POSTAL_CODE_ATTRIBUTE = "postal_code";

    private final UserRepository userRepository;
    private final KeycloakAdminService keycloakAdminService;
    private final UserMapper userMapper;

    @Transactional
    public void syncUsersWithKeycloak() {
        log.info("Starting user synchronization with Keycloak...");

        List<UserRepresentation> keycloakUsers = keycloakAdminService.getAllUsers();
        List<String> activeKeycloakIds = extractKeycloakIds(keycloakUsers);

        removeOrphanedUsers(activeKeycloakIds);
        processUsersSynchronization(keycloakUsers);

        log.info("User synchronization completed. Processed {} users.", keycloakUsers.size());
    }

    private List<String> extractKeycloakIds(List<UserRepresentation> users) {
        return users.stream()
                .map(UserRepresentation::getId)
                .collect(Collectors.toList());
    }

    private void removeOrphanedUsers(List<String> activeKeycloakIds) {
        List<User> usersToRemove = userRepository.findAll().stream()
                .filter(user -> !activeKeycloakIds.contains(user.getKeycloakId()))
                .peek(user -> log.info("Removing orphaned user: {}", user.getUsername()))
                .collect(Collectors.toList());

        if (!usersToRemove.isEmpty()) {
            userRepository.deleteAll(usersToRemove);
        }
    }

    private void processUsersSynchronization(List<UserRepresentation> keycloakUsers) {
        keycloakUsers.forEach(kcUser -> {
            List<String> roles = synchronizeUserRoles(kcUser);
            synchronizeUserData(kcUser, roles);
        });
    }

    private List<String> synchronizeUserRoles(UserRepresentation kcUser) {
        List<String> roles = keycloakAdminService.getUserRoles(kcUser.getId());
        String roleAttribute = keycloakAdminService.getUserRoleAttribute(kcUser);

        if (shouldAssignNewRole(roleAttribute, roles)) {
            keycloakAdminService.assignRealmRoleToUser(kcUser.getId(), roleAttribute);
            roles.add(roleAttribute);
        }

        return roles;
    }

    private boolean shouldAssignNewRole(String roleAttribute, List<String> roles) {
        return roleAttribute != null && !roles.contains(roleAttribute);
    }

    private void synchronizeUserData(UserRepresentation kcUser, List<String> roles) {
        String userId = kcUser.getId();
        userRepository.findByKeycloakId(userId)
                .ifPresentOrElse(
                        existingUser -> updateExistingUser(existingUser, kcUser, roles),
                        () -> createNewUser(kcUser, roles)
                );
    }

    private void updateExistingUser(User user, UserRepresentation kcUser, List<String> roles) {
        log.debug("Updating existing user: {}", user.getUsername());
        userMapper.updateFromKeycloak(user, kcUser);
        user.setRoles(roles);
        userRepository.save(user);
    }

    private void createNewUser(UserRepresentation kcUser, List<String> roles) {
        log.info("Creating new user with Keycloak ID: {}", kcUser.getId());
        User newUser = new User();
        userMapper.updateFromKeycloak(newUser, kcUser);
        newUser.setKeycloakId(kcUser.getId());
        newUser.setRoles(roles);
        userRepository.save(newUser);
    }

    @Transactional(readOnly = true)
    public UserResponse findByKeycloakId(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new UserNotFoundException("User not found with Keycloak ID: " + keycloakId));
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long userId) {
        return userRepository.findById(userId)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + userId));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAllUsers() {
        log.info("Fetching all users from database");
        return userRepository.findAll().stream()
                .map(userMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void syncSingleUser(String userId) {
        log.info("Synchronizing single user: {}", userId);
        UserRepresentation kcUser = keycloakAdminService.getUserById(userId);
        List<String> roles = synchronizeUserRoles(kcUser);
        synchronizeUserData(kcUser, roles);
    }

    private String getFirstAttribute(UserRepresentation kcUser, String attributeName) {
        return kcUser.getAttributes() != null
                ? kcUser.getAttributes().getOrDefault(attributeName, List.of()).stream().findFirst().orElse(null)
                : null;
    }


    private User getUserOrThrow(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }
    @Transactional
    public void updateUserPicture(String keycloakId, String pictureUrl) {
        User user = getUserOrThrow(keycloakId);
        user.setPictureUrl(pictureUrl);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        log.info("Updated picture for user: {}", keycloakId);
    }
    @Transactional
    public void updateUserAddress(String keycloakId, AddressUpdateRequest request) {
        User user = getUserOrThrow(keycloakId);

        Address address = Optional.ofNullable(user.getAddress())
                .orElseGet(Address::new);

        address.setCity(request.city());
        address.setCountry(request.country());
        address.setPostalCode(request.postalCode());

        user.setAddress(address);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        log.info("Updated address for user: {}", keycloakId);
    }
    @Transactional
    public void updateUserProfile(String keycloakId, UserProfileUpdateRequest request) {
        User user = getUserOrThrow(keycloakId);

        // Update basic info
        if (request.firstName() != null) user.setFirstName(request.firstName());
        if (request.lastName() != null) user.setLastName(request.lastName());
        if (request.phoneNumber() != null) user.setPhoneNumber(request.phoneNumber());

        // Update picture
        if (request.pictureUrl() != null) user.setPictureUrl(request.pictureUrl());

        // Update address
        if (request.address() != null) {
            updateUserAddress(keycloakId, request.address());
        }

        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        log.info("Updated profile for user: {}", keycloakId);
    }


}