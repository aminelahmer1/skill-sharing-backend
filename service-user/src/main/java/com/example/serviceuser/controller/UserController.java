package com.example.serviceuser.controller;
import jakarta.validation.Valid;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.security.access.prepost.PreAuthorize;
import com.example.serviceuser.configuration.KeycloakAdminService;

import com.example.serviceuser.dto.*;
import com.example.serviceuser.exception.UserNotFoundException;
import com.example.serviceuser.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin
public class UserController {

    private final UserService userService;
    private final JwtDecoder jwtDecoder;
    private final KeycloakAdminService keycloakAdminService;
    @PostMapping("/sync")
    public ResponseEntity<String> syncUsers() {
        log.info("Manual synchronization request received...");
        userService.syncUsersWithKeycloak();
        return ResponseEntity.ok("User synchronization completed successfully.");
    }

    @GetMapping
    public ResponseEntity<UserResponse> getUserByKeycloakId(@RequestParam(required = false) String keycloakId) {
        if (keycloakId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(userService.findByKeycloakId(keycloakId));
    }

    @GetMapping("/all")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        log.info("Request received to fetch all users...");
        List<UserResponse> users = userService.findAllUsers();
        return ResponseEntity.ok(users);
    }

    @PostMapping(value = "/{keycloakId}/picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserResponse> updateUserPicture(
            @PathVariable String keycloakId,
            @RequestParam("file") MultipartFile file,
            @RequestHeader("Authorization") String token) {

        validateUserAccess(keycloakId, token);
        UserResponse updatedUser = userService.updateUserPicture(keycloakId, file);
        return ResponseEntity.ok(updatedUser);
    }

    @PatchMapping("/{keycloakId}/address")
    public ResponseEntity<Void> updateAddress(
            @PathVariable String keycloakId,
            @RequestBody AddressUpdateRequest request) {
        userService.updateUserAddress(keycloakId, request);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{keycloakId}/profile")
    public ResponseEntity<UserResponse> updateProfile(
            @PathVariable String keycloakId,
            @RequestBody CombinedProfileUpdateRequest request) {  // Changé ici
        UserResponse updatedUser = userService.updateUserProfile(keycloakId, request);
        return ResponseEntity.ok(updatedUser);
    }


    private String extractKeycloakIdFromToken(String token) {
        try {
            String cleanToken = token.replace("Bearer ", "");
            Jwt jwt = jwtDecoder.decode(cleanToken);
            return jwt.getSubject(); // Extract Keycloak ID (sub field)
        } catch (Exception ex) {
            log.error("Error decoding token: {}", ex.getMessage());
            throw new RuntimeException("Invalid or expired token");
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserProfileResponse> getUserById(@PathVariable Long id) {
        UserProfileResponse user = userService.findProfileById(id);
        return ResponseEntity.ok(user);
    }

    private void validateUserAccess(String requestedKeycloakId, String token) {
        String tokenKeycloakId = extractKeycloakIdFromToken(token);
        if (!requestedKeycloakId.equals(tokenKeycloakId)) {
            throw new AccessDeniedException("You can only modify your own profile");
        }
    }

    @PostMapping("/admincreate")
    public ResponseEntity<UserResponse> createUser(@RequestBody @Valid UserCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userService.createUserWithKeycloakSync(request));
    }

    @PutMapping("/{keycloakId}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable String keycloakId,
            @RequestBody UserUpdateRequest request,
            @RequestHeader("Authorization") String token) {

        // Vérification de l'identité
        String tokenKeycloakId = extractKeycloakIdFromToken(token);
        if(!keycloakId.equals(tokenKeycloakId)) {
            throw new AccessDeniedException("Unauthorized access");
        }

        return ResponseEntity.ok(userService.updateUserWithSync(keycloakId, request));
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUserProfile(
            @RequestHeader("Authorization") String token) {
        String keycloakId = extractKeycloakIdFromToken(token);
        UserProfileResponse profile = userService.getCompleteUserProfile(keycloakId);
        return ResponseEntity.ok(profile);
    }

    @PatchMapping("/{keycloakId}/keycloak-profile")
    public ResponseEntity<UserResponse> updateKeycloakProfile(
            @PathVariable String keycloakId,
            @RequestBody @Valid KeycloakProfileUpdateRequest request) {
        return ResponseEntity.ok(userService.updateKeycloakProfile(keycloakId, request));
    }

    @PatchMapping("/{keycloakId}/local-profile")
    public ResponseEntity<UserResponse> updateLocalProfile(
            @PathVariable String keycloakId,
            @RequestBody @Valid LocalProfileUpdateRequest request) {
        return ResponseEntity.ok(userService.updateLocalProfile(keycloakId, request));
    }

    @PatchMapping("/{keycloakId}/picture")
    public ResponseEntity<UserResponse> updateProfilePicture(
            @PathVariable String keycloakId,
            @RequestParam String pictureUrl) {
        UserResponse updatedUser = userService.updateProfilePicture(keycloakId, pictureUrl);
        return ResponseEntity.ok(updatedUser);
    }
}
