package com.example.serviceuser.configuration;

import com.example.serviceuser.dto.UserCreateRequest;
import com.example.serviceuser.dto.UserUpdateRequest;
import com.example.serviceuser.exception.RoleAssignmentException;
import com.example.serviceuser.exception.UserCreationException;
import com.example.serviceuser.exception.UserNotFoundException;
import com.example.serviceuser.exception.UserSyncException;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KeycloakAdminService {

    private static final String REALM = "skill-sharing";

    private final Keycloak keycloak;

    public List<UserRepresentation> getAllUsers() {
        try {
            log.info("Fetching all users from Keycloak for realm: {}", REALM);
            return keycloak.realm(REALM).users().search(null, null, null, null, 0, Integer.MAX_VALUE);
        } catch (Exception e) {
            log.error("Failed to fetch users from Keycloak: {}", e.getMessage(), e);
            throw new UserSyncException("Failed to fetch users from Keycloak", e);
        }
    }

    public UserRepresentation getUserById(String userId) {
        try {
            log.info("Fetching user with ID '{}' from Keycloak", userId);
            return keycloak.realm(REALM).users().get(userId).toRepresentation();
        } catch (Exception e) {
            log.error("Failed to fetch user with ID '{}': {}", userId, e.getMessage(), e);
            throw new UserSyncException("Failed to fetch user from Keycloak", e);
        }
    }

    public List<String> getUserRoles(String userId) {
        try {
            log.debug("Fetching roles for user ID '{}'", userId);
            UserResource userResource = keycloak.realm(REALM).users().get(userId);
            return userResource.roles().realmLevel().listAll().stream()
                    .map(RoleRepresentation::getName)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to fetch roles for user ID '{}': {}", userId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    public String getUserRoleAttribute(UserRepresentation user) {
        if (user.getAttributes() != null && user.getAttributes().containsKey("role")) {
            return user.getAttributes().get("role").stream().findFirst().orElse(null);
        }
        return null;
    }

    public void assignRealmRoleToUser(String userId, String roleName) {
        try {
            log.info("Assigning role '{}' to user '{}'", roleName, userId);
            UserResource userResource = keycloak.realm(REALM).users().get(userId);
            RoleRepresentation role = keycloak.realm(REALM).roles().get(roleName).toRepresentation();
            userResource.roles().realmLevel().add(List.of(role));
        } catch (Exception e) {
            log.error("Failed to assign role '{}' to user '{}': {}", roleName, userId, e.getMessage(), e);
            throw new RoleAssignmentException("Failed to assign role to user", e);
        }
    }

    public String createUserInKeycloak(UserCreateRequest request) {
        try {
            log.info("Creating user in Keycloak with email: {}", request.email());
            UserRepresentation user = new UserRepresentation();
            user.setUsername(request.username());
            user.setEmail(request.email());
            user.setFirstName(request.firstName());
            user.setLastName(request.lastName());
            user.setEnabled(true);
            user.setEmailVerified(false); // Assure que l'email doit être vérifié

            Map<String, List<String>> attributes = new HashMap<>();
            attributes.put("phone-mapper", List.of(request.phoneNumber()));
            attributes.put("role", List.of(request.role()));
            user.setAttributes(attributes);

            Response response = keycloak.realm(REALM).users().create(user);
            if (response.getStatus() != 201) {
                String error = response.readEntity(String.class);
                log.error("Failed to create user in Keycloak: {}", error);
                throw new UserCreationException("Failed to create user in Keycloak: " + error);
            }

            String userId = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");
            log.info("Created user in Keycloak with ID: {}", userId);

            // Assign role and set password
            assignRealmRoleToUser(userId, request.role());
            setUserPassword(userId, request.password());

            // Trigger email verification
            sendVerificationEmail(userId);

            return userId;
        } catch (Exception e) {
            log.error("Failed to create user in Keycloak: {}", e.getMessage(), e);
            throw new UserCreationException("Failed to create user in Keycloak", e);
        }
    }

    public void updateUserInKeycloak(String keycloakId, UserUpdateRequest request) {
        try {
            log.info("Updating user in Keycloak with ID: {}", keycloakId);
            UserResource userResource = keycloak.realm(REALM).users().get(keycloakId);
            UserRepresentation user = userResource.toRepresentation();

            Map<String, List<String>> attributes = user.getAttributes() != null
                    ? new HashMap<>(user.getAttributes())
                    : new HashMap<>();
            attributes.put("phone-mapper", List.of(request.phoneNumber()));
            user.setAttributes(attributes);

            userResource.update(user);
        } catch (Exception e) {
            log.error("Failed to update user in Keycloak with ID '{}': {}", keycloakId, e.getMessage(), e);
            throw new UserSyncException("Failed to update user in Keycloak", e);
        }
    }

    public void sendVerificationEmail(String userId) {
        try {
            log.info("Sending verification email for user ID: {}", userId);
            UserResource userResource = keycloak.realm(REALM).users().get(userId);
            userResource.sendVerifyEmail();
            log.info("Verification email sent successfully for user ID: {}", userId);
        } catch (Exception e) {
            log.error("Failed to send verification email for user ID '{}': {}", userId, e.getMessage(), e);
            throw new UserSyncException("Failed to send verification email", e);
        }
    }

    public void resendVerificationEmail(String email) {
        try {
            log.info("Resending verification email for email: {}", email);
            List<UserRepresentation> users = keycloak.realm(REALM).users().searchByEmail(email, true);
            if (users.isEmpty()) {
                log.error("No user found with email: {}", email);
                throw new UserNotFoundException("No user found with email: " + email);
            }
            String userId = users.get(0).getId();
            UserResource userResource = keycloak.realm(REALM).users().get(userId);
            userResource.sendVerifyEmail();
            log.info("Verification email resent successfully for email: {}", email);
        } catch (Exception e) {
            log.error("Failed to resend verification email for email '{}': {}", email, e.getMessage(), e);
            throw new UserSyncException("Failed to resend verification email", e);
        }
    }

    private void setUserPassword(String userId, String password) {
        try {
            log.debug("Setting password for user ID: {}", userId);
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(password);
            credential.setTemporary(false);
            keycloak.realm(REALM).users().get(userId).resetPassword(credential);
        } catch (Exception e) {
            log.error("Failed to set password for user ID '{}': {}", userId, e.getMessage(), e);
            throw new UserSyncException("Failed to set user password", e);
        }
    }

    public UserRepresentation getUserByUsername(String username) {
        try {
            log.info("Fetching user with username: {}", username);
            List<UserRepresentation> users = keycloak.realm(REALM).users().search(username, true);
            if (users.isEmpty()) {
                log.error("User not found with username: {}", username);
                throw new UserNotFoundException("User not found with username: " + username);
            }
            return users.get(0);
        } catch (Exception e) {
            log.error("Failed to fetch user with username '{}': {}", username, e.getMessage(), e);
            throw new UserSyncException("Failed to fetch user from Keycloak", e);
        }
    }

    public boolean userExists(String username, String email) {
        try {
            log.debug("Checking if user exists with username '{}' or email '{}'", username, email);
            return !keycloak.realm(REALM).users().search(username, true).isEmpty() ||
                    !keycloak.realm(REALM).users().searchByEmail(email, true).isEmpty();
        } catch (Exception e) {
            log.error("Failed to check user existence for username '{}' or email '{}': {}", username, email, e.getMessage(), e);
            return false;
        }
    }

    public void updateKeycloakUserBasicInfo(String keycloakId, String username, String firstName, String lastName) {
        try {
            log.info("Updating basic info for user ID: {}", keycloakId);
            UserResource userResource = keycloak.realm(REALM).users().get(keycloakId);
            UserRepresentation user = userResource.toRepresentation();
            user.setUsername(username);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            userResource.update(user);
            log.info("Successfully updated basic info for user ID: {}", keycloakId);
        } catch (Exception e) {
            log.error("Failed to update basic info for user ID '{}': {}", keycloakId, e.getMessage(), e);
            throw new UserSyncException("Failed to update user in Keycloak", e);
        }
    }

    public void updateKeycloakUserPartial(String keycloakId, Map<String, Object> updates) {
        try {
            log.info("Partially updating user ID '{}' with fields: {}", keycloakId, updates.keySet());
            UserResource userResource = keycloak.realm(REALM).users().get(keycloakId);
            UserRepresentation user = userResource.toRepresentation();
            boolean hasChanges = false;

            if (updates.containsKey("username")) {
                user.setUsername((String) updates.get("username"));
                hasChanges = true;
            }
            if (updates.containsKey("firstName")) {
                user.setFirstName((String) updates.get("firstName"));
                hasChanges = true;
            }
            if (updates.containsKey("lastName")) {
                user.setLastName((String) updates.get("lastName"));
                hasChanges = true;
            }
            if (updates.containsKey("phoneNumber")) {
                Map<String, List<String>> attributes = user.getAttributes() != null
                        ? new HashMap<>(user.getAttributes())
                        : new HashMap<>();
                attributes.put("phone-mapper", List.of((String) updates.get("phoneNumber")));
                user.setAttributes(attributes);
                hasChanges = true;
            }

            if (hasChanges) {
                userResource.update(user);
                log.info("Successfully updated user ID: {}", keycloakId);
            }
        } catch (Exception e) {
            log.error("Failed to partially update user ID '{}': {}", keycloakId, e.getMessage(), e);
            throw new UserSyncException("Failed to update user in Keycloak", e);
        }
    }

    public void forceUpdateKeycloakFields(String keycloakId, String username, String firstName, String lastName, String phoneNumber) {
        try {
            log.info("Forcing update for user ID: {}", keycloakId);
            UserResource userResource = keycloak.realm(REALM).users().get(keycloakId);
            UserRepresentation user = userResource.toRepresentation();

            if (username != null) user.setUsername(username);
            if (firstName != null) user.setFirstName(firstName);
            if (lastName != null) user.setLastName(lastName);
            if (phoneNumber != null) {
                Map<String, List<String>> attributes = user.getAttributes() != null
                        ? new HashMap<>(user.getAttributes())
                        : new HashMap<>();
                attributes.put("phone-mapper", List.of(phoneNumber));
                user.setAttributes(attributes);
            }

            userResource.update(user);
            log.info("Successfully forced update for user ID: {}", keycloakId);
        } catch (Exception e) {
            log.error("Failed to force update user ID '{}': {}", keycloakId, e.getMessage(), e);
            throw new UserSyncException("Failed to update user in Keycloak", e);
        }
    }
}