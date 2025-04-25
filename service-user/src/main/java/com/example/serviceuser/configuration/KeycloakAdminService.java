package com.example.serviceuser.configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class KeycloakAdminService {

    private final Keycloak keycloak;

    public List<UserRepresentation> getAllUsers() {
        try {
            log.info("Fetching all users from Keycloak...");
            return keycloak.realm("skill-sharing")
                    .users()
                    .search(null, null, null, null, 0, Integer.MAX_VALUE);
        } catch (Exception e) {
            log.error("Error fetching users from Keycloak: {}", e.getMessage());
            throw new RuntimeException("Error fetching users from Keycloak", e);
        }
    }

    public UserRepresentation getUserById(String userId) {
        try {
            log.info("Fetching user with ID '{}' from Keycloak...", userId);
            return keycloak.realm("skill-sharing").users().get(userId).toRepresentation();
        } catch (Exception e) {
            log.error("Error fetching user with ID '{}': {}", userId, e.getMessage());
            throw new RuntimeException("Error fetching user from Keycloak", e);
        }
    }

    public List<String> getUserRoles(String userId) {
        List<String> roles = new ArrayList<>();
        try {
            UserResource userResource = keycloak.realm("skill-sharing").users().get(userId);
            List<RoleRepresentation> realmRoles = userResource.roles().realmLevel().listAll();
            for (RoleRepresentation role : realmRoles) {
                roles.add(role.getName());
            }
        } catch (Exception e) {
            log.error("Error fetching roles for user ID '{}': {}", userId, e);
        }
        return roles;
    }

    public String getUserRoleAttribute(UserRepresentation userRepresentation) {
        if (userRepresentation.getAttributes() != null) {
            return userRepresentation.getAttributes()
                    .getOrDefault("role", List.of())
                    .stream()
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    public void assignRealmRoleToUser(String userId, String roleName) {
        try {
            UserResource userResource = keycloak.realm("skill-sharing").users().get(userId);
            RoleRepresentation role = keycloak.realm("skill-sharing").roles().get(roleName).toRepresentation();
            userResource.roles().realmLevel().add(List.of(role));
            log.info("Assigned role '{}' to user '{}'", roleName, userId);
        } catch (Exception e) {
            log.error("Error assigning role '{}' to user '{}': {}", roleName, userId, e.getMessage());
        }
    }
}
