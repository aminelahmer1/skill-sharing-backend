package com.example.servicemessagerie.util;

import com.example.servicemessagerie.dto.UserResponse;
import com.example.servicemessagerie.feignclient.UserServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import feign.FeignException;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserIdResolver {

    private final UserServiceClient userServiceClient;

    // Pattern pour détecter un UUID Keycloak
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    @Cacheable(value = "userIds", key = "#jwt.subject")
    public Long resolveUserId(Jwt jwt, String token) {
        String subject = jwt.getSubject();
        log.debug("Resolving user ID for subject: {}", subject);

        // Vérifier d'abord si c'est un UUID (format Keycloak)
        if (UUID_PATTERN.matcher(subject).matches()) {
            log.debug("Subject is UUID format, resolving from Keycloak ID");
            return resolveFromKeycloakId(subject, token);
        }

        // Sinon, essayer de parser comme Long
        try {
            Long userId = Long.parseLong(subject);
            log.debug("Subject parsed as Long: {}", userId);
            return userId;
        } catch (NumberFormatException e) {
            log.warn("Subject '{}' is neither UUID nor valid Long", subject);
            throw new IllegalArgumentException("Invalid subject format: " + subject);
        }
    }

    @Cacheable(value = "userIds", key = "'keycloak:' + #keycloakId")
    public Long resolveFromKeycloakId(String keycloakId, String token) {
        log.debug("Resolving user ID from Keycloak ID: {}", keycloakId);

        try {
            ResponseEntity<UserResponse> response = userServiceClient.getUserByKeycloakId(keycloakId, token);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                UserResponse user = response.getBody();
                if (user.id() != null) {
                    log.debug("✅ Resolved user ID: {} for Keycloak ID: {}", user.id(), keycloakId);
                    return user.id();
                }
            }

            throw new IllegalArgumentException("User not found for Keycloak ID: " + keycloakId);

        } catch (FeignException.NotFound ex) {
            log.error("❌ User not found for Keycloak ID: {}", keycloakId);
            throw new IllegalArgumentException("User not found for Keycloak ID: " + keycloakId);
        } catch (FeignException ex) {
            log.error("❌ Feign error while resolving user ID: {}", ex.getMessage());
            throw new RuntimeException("Error calling user service: " + ex.getMessage());
        }
    }

    /**
     * Récupère les informations complètes d'un utilisateur
     */
    @Cacheable(value = "userDetails", key = "#userId")
    public UserResponse getUserDetails(Long userId, String token) {
        try {
            ResponseEntity<UserResponse> response = userServiceClient.getUserById(userId, token);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            throw new IllegalArgumentException("User not found with ID: " + userId);
        } catch (FeignException ex) {
            log.error("Error fetching user details for ID {}: {}", userId, ex.getMessage());
            throw new RuntimeException("Error fetching user details: " + ex.getMessage());
        }
    }

    /**
     * Vérifie si un utilisateur a un rôle spécifique
     */
    public boolean hasRole(Jwt jwt, String role) {
        try {
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess != null) {
                List<String> roles = (List<String>) realmAccess.get("roles");
                return roles != null && roles.contains(role);
            }
            return false;
        } catch (Exception e) {
            log.warn("Error checking role '{}' for user: {}", role, e.getMessage());
            return false;
        }
    }

    /**
     * Récupère tous les rôles d'un utilisateur
     */
    public List<String> getUserRoles(Jwt jwt) {
        try {
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess != null) {
                List<String> roles = (List<String>) realmAccess.get("roles");
                return roles != null ? roles : List.of();
            }
            return List.of();
        } catch (Exception e) {
            log.warn("Error getting user roles: {}", e.getMessage());
            return List.of();
        }
    }
}