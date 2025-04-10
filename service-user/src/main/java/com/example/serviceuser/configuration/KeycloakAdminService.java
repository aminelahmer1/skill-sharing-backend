package com.example.serviceuser.configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RoleMappingResource;
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

    /**
     * Fetch all users from Keycloak.
     *
     * @return List of users from Keycloak.
     */
    public List<UserRepresentation> getAllUsers() {
        try {
            log.info("Fetching all users from Keycloak...");
            return keycloak.realm("skill-sharing").users().search(null, null, null, null, 0, Integer.MAX_VALUE);
        } catch (Exception e) {
            log.error("Failed to fetch users from Keycloak: {}", e.getMessage());
            throw new RuntimeException("Error fetching users from Keycloak", e);
        }
    }
    public List<String> getUserRoles(String userId) {
        List<String> roles = new ArrayList<>();
        try {
            // Retrieve the user resource
            UserResource userResource = keycloak
                    .realm("skill-sharing")
                    .users()
                    .get(userId);

            // Fetch realm-level role mappings
            List<RoleRepresentation> realmRoles = userResource.roles().realmLevel().listAll();
            for (RoleRepresentation role : realmRoles) {
                roles.add(role.getName());
            }
        } catch (Exception e) {
            log.error("Failed to fetch roles for user ID: {}", userId, e);
        }
        return roles;
    }

}
