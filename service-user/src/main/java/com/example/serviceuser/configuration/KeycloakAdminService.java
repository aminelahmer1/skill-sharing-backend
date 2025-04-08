package com.example.serviceuser.configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;

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
}
