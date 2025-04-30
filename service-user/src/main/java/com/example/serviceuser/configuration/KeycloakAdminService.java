package com.example.serviceuser.configuration;

import com.example.serviceuser.dto.UserCreateRequest;
import com.example.serviceuser.exception.RoleAssignmentException;

import com.example.serviceuser.exception.UserCreationException;
import com.example.serviceuser.exception.UserSyncException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.*;

import com.example.serviceuser.dto.UserUpdateRequest;
import org.keycloak.representations.idm.CredentialRepresentation;
@Service
@RequiredArgsConstructor
@Slf4j
@CrossOrigin
public class KeycloakAdminService {

    private final Keycloak keycloak;
    private static final String REALM = "skill-sharing";
    private static final String TEMP_PASSWORD = "temporaryPassword123!";
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


    public String createUserInKeycloak(UserCreateRequest request) {
        try {
            // Créer la représentation utilisateur
            UserRepresentation user = new UserRepresentation();
            user.setUsername(request.username());
            user.setEmail(request.email());
            user.setFirstName(request.firstName());
            user.setLastName(request.lastName());
            user.setEnabled(true);
            user.setEmailVerified(true);


            // Configurer les attributs
            Map<String, List<String>> attributes = new HashMap<>();
            attributes.put("phone-mapper", List.of(request.phoneNumber()));
            attributes.put("role", List.of(request.role()));
            user.setAttributes(attributes);

            // Créer l'utilisateur dans Keycloak
            Response response = keycloak.realm(REALM).users().create(user);

            if (response.getStatus() != 201) {
                throw new UserCreationException("Échec de la création dans Keycloak : " + response.getStatusInfo());
            }

            // Récupérer l'ID de l'utilisateur créé
            String userId = response.getLocation().getPath()
                    .replaceAll(".*/([^/]+)$", "$1");

            // Assigner le rôle
            assignRealmRoleToUser(userId, request.role());

            // Définir le mot de passe
            setUserPassword(userId, "temporaryPassword123!");

            return userId;

        } catch (Exception e) {
            log.error("Échec de la création Keycloak : {}", e.getMessage());
            throw new UserSyncException("Échec de la synchronisation avec Keycloak", e);
        }
    }
    public void updateUserInKeycloak(String keycloakId, UserUpdateRequest request) {
        try {
            UserResource userResource = keycloak.realm(REALM).users().get(keycloakId);
            UserRepresentation user = userResource.toRepresentation();

            Map<String, List<String>> attributes = user.getAttributes() != null ?
                    new HashMap<>(user.getAttributes()) : new HashMap<>();

            attributes.put("phone-mapper", List.of(request.phoneNumber()));
            user.setAttributes(attributes);

            userResource.update(user);
        } catch (Exception e) {
            log.error("Keycloak user update failed: {}", e.getMessage());
            throw new RuntimeException("Keycloak user update failed", e);
        }
    }
    private void setUserPassword(String userId, String password) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(true);

        keycloak.realm(REALM)
                .users()
                .get(userId)
                .resetPassword(credential);
    }
    private String getCreatedUserId(Response response) {
        return response.getLocation().getPath()
                .replaceAll(".*/([^/]+)$", "$1");
    }

    private void handleKeycloakResponse(Response response) {
        if (response.getStatus() != 201) {
            String error = response.readEntity(String.class);
            throw new RuntimeException("Keycloak error: " + error);
        }
    }

    public UserRepresentation getUserByUsername(String username) {
        List<UserRepresentation> users = keycloak.realm(REALM)
                .users()
                .search(username);

        if(users.isEmpty()) {
            throw new RuntimeException("User not found with username: " + username);
        }

        return users.get(0);
    }

    public boolean userExists(String username, String email) {
        List<UserRepresentation> usersByUsername = keycloak.realm(REALM).users().search(username);
        if (!usersByUsername.isEmpty()) return true;

        List<UserRepresentation> usersByEmail = keycloak.realm(REALM).users().searchByEmail(email, true);
        return !usersByEmail.isEmpty();
    }

    public void updateKeycloakUserBasicInfo(String keycloakId, String username, String firstName, String lastName) {
        try {
            UserResource userResource = keycloak.realm(REALM).users().get(keycloakId);
            UserRepresentation user = userResource.toRepresentation();

            // Mise à jour uniquement des champs de base
            user.setUsername(username);
            user.setFirstName(firstName);
            user.setLastName(lastName);

            userResource.update(user);
            log.info("Mise à jour Keycloak réussie pour {}", keycloakId);

        } catch (Exception e) {
            log.error("Échec mise à jour Keycloak: {}", e.getMessage());
            throw new RuntimeException("Erreur Keycloak", e);
        }
    }
    public void updateKeycloakUserPartial(String keycloakId, Map<String, Object> updates) {
        try {
            UserResource userResource = keycloak.realm(REALM).users().get(keycloakId);
            UserRepresentation user = userResource.toRepresentation();
            boolean hasChanges = false;

            // Mise à jour des champs de base
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

            // Mise à jour du téléphone dans les attributs
            if (updates.containsKey("phoneNumber")) {
                Map<String, List<String>> attributes = user.getAttributes() != null ?
                        new HashMap<>(user.getAttributes()) : new HashMap<>();
                attributes.put("phone-mapper", List.of((String) updates.get("phoneNumber")));
                user.setAttributes(attributes);
                hasChanges = true;
            }

            if (hasChanges) {
                userResource.update(user);
                log.info("Mise à jour Keycloak partielle réussie pour {} - Champs: {}",
                        keycloakId, updates.keySet());
            }

        } catch (Exception e) {
            log.error("Échec mise à jour Keycloak partielle", e);
            throw new UserSyncException("Erreur de synchronisation Keycloak", e);
        }
    }
    public void forceUpdateKeycloakFields(String keycloakId,
                                          String username,
                                          String firstName,
                                          String lastName,
                                          String phoneNumber) {
        try {
            UserResource userResource = keycloak.realm(REALM).users().get(keycloakId);
            UserRepresentation user = userResource.toRepresentation();

            if (username != null) user.setUsername(username);
            if (firstName != null) user.setFirstName(firstName);
            if (lastName != null) user.setLastName(lastName);

            // Mise à jour du phone dans les attributs Keycloak
            if (phoneNumber != null) {
                Map<String, List<String>> attributes = user.getAttributes() != null ?
                        new HashMap<>(user.getAttributes()) : new HashMap<>();
                attributes.put("phone-mapper", List.of(phoneNumber));
                user.setAttributes(attributes);
            }

            userResource.update(user);
            log.info("Mise à jour Keycloak forcée pour {}", keycloakId);

        } catch (Exception e) {
            log.error("Échec mise à jour Keycloak", e);
            throw new UserSyncException("Erreur de synchronisation Keycloak", e);
        }
    }

}
