package com.example.serviceuser.service;

import com.example.serviceuser.configuration.KeycloakAdminService;
import com.example.serviceuser.dto.*;
import com.example.serviceuser.entity.Address;
import com.example.serviceuser.entity.User;
import com.example.serviceuser.exception.UserAlreadyExistsException;
import com.example.serviceuser.exception.UserNotFoundException;
import com.example.serviceuser.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private static final String PHONE_ATTRIBUTE = "phone-mapper";

    private final UserRepository userRepository;
    private final KeycloakAdminService keycloakAdminService;
    private final UserMapper userMapper;
    private final FileStorageService fileStorageService;
    @Transactional
    public void syncUsersWithKeycloak() {
        log.info("Starting user synchronization with Keycloak...");

        List<UserRepresentation> keycloakUsers = keycloakAdminService.getAllUsers();
        processUsersSynchronization(keycloakUsers);

        log.info("User synchronization completed. Processed {} users.", keycloakUsers.size());
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

    private void synchronizeUserData(UserRepresentation kcUser, List<String> newRoles) {
        String userId = kcUser.getId();
        userRepository.findByKeycloakId(userId)
                .ifPresentOrElse(
                        existingUser -> updateExistingUser(existingUser, kcUser, newRoles),
                        () -> createNewUser(kcUser, newRoles)
                );
    }

    private void updateExistingUser(User user, UserRepresentation kcUser, List<String> newRoles) {
        // 1. Sauvegarde des champs à préserver
        String originalPictureUrl = user.getPictureUrl();
        Address originalAddress = user.getAddress();

        // 2. Mise à jour des champs synchronisés
        user.setUsername(kcUser.getUsername());
        user.setEmail(kcUser.getEmail());
        user.setFirstName(kcUser.getFirstName());
        user.setLastName(kcUser.getLastName());

        // 3. Mise à jour conditionnelle du téléphone
        if (kcUser.getAttributes() != null && kcUser.getAttributes().containsKey(PHONE_ATTRIBUTE)) {
            String newPhone = kcUser.getAttributes().get(PHONE_ATTRIBUTE).get(0);
            if (!newPhone.equals(user.getPhoneNumber())) {
                user.setPhoneNumber(newPhone);
            }
        }

        // 4. Mise à jour conditionnelle des rôles
        if (!newRoles.equals(user.getRoles())) {
            user.setRoles(newRoles);
        }

        // 5. Restauration des champs préservés
        user.setPictureUrl(originalPictureUrl);
        user.setAddress(originalAddress);

        // 6. Sauvegarde uniquement si nécessaire
        if (user.getUpdatedAt() == null ||
                Duration.between(user.getUpdatedAt(), LocalDateTime.now()).toMinutes() > 5) {
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
        }
    }

    private void createNewUser(UserRepresentation kcUser, List<String> roles) {
        log.info("Creating new user with Keycloak ID: {}", kcUser.getId());
        User newUser = new User();
        userMapper.updateFromKeycloak(newUser, kcUser);
        newUser.setKeycloakId(kcUser.getId());
        newUser.setRoles(roles);

        // Valeurs par défaut pour les champs non synchronisés
        newUser.setPictureUrl(null);
        newUser.setAddress(null);

        userRepository.save(newUser);
    }

    // Les autres méthodes restent inchangées...
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

    private User getUserOrThrow(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }



    @Transactional(readOnly = true)
    public UserProfileResponse findProfileByKeycloakId(String keycloakId) {
        log.info("Fetching user profile with Keycloak ID: {}", keycloakId);
        return userRepository.findByKeycloakId(keycloakId)
                .map(userMapper::toProfileResponse)
                .orElseThrow(() -> new UserNotFoundException("User not found with Keycloak ID: " + keycloakId));
    }

    @Transactional(readOnly = true)
    public UserProfileResponse findProfileById(Long id) {
        return userRepository.findById(id)
                .map(userMapper::toProfileResponse)
                .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + id));
    }

    @Transactional
    public UserResponse updateUserProfile(String keycloakId, CombinedProfileUpdateRequest request) {
        User user = getUserOrThrow(keycloakId);

        // Détection des changements pour Keycloak
        Map<String, Object> keycloakUpdates = new HashMap<>();

        // Mise à jour conditionnelle des champs
        if (request.username() != null && !request.username().equals(user.getUsername())) {
            user.setUsername(request.username());
            keycloakUpdates.put("username", request.username());
        }

        if (request.firstName() != null && !request.firstName().equals(user.getFirstName())) {
            user.setFirstName(request.firstName());
            keycloakUpdates.put("firstName", request.firstName());
        }

        if (request.lastName() != null && !request.lastName().equals(user.getLastName())) {
            user.setLastName(request.lastName());
            keycloakUpdates.put("lastName", request.lastName());
        }

        if (request.phoneNumber() != null && !request.phoneNumber().equals(user.getPhoneNumber())) {
            user.setPhoneNumber(request.phoneNumber());
            keycloakUpdates.put("phoneNumber", request.phoneNumber());
        }

        // Mise à jour des champs locaux
        if (request.pictureUrl() != null) {
            user.setPictureUrl(request.pictureUrl());
        }

        if (request.address() != null) {
            Address address = Optional.ofNullable(user.getAddress())
                    .orElseGet(Address::new);
            if (request.address().city() != null) address.setCity(request.address().city());
            if (request.address().country() != null) address.setCountry(request.address().country());
            if (request.address().postalCode() != null) address.setPostalCode(request.address().postalCode());
            user.setAddress(address);
        }

        // Synchronisation avec Keycloak si nécessaire
        if (!keycloakUpdates.isEmpty()) {
            keycloakAdminService.updateKeycloakUserPartial(keycloakId, keycloakUpdates);
        }

        userRepository.save(user);
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updateUserPicture(String keycloakId, MultipartFile file) {
        User user = getUserOrThrow(keycloakId);
        String pictureUrl = fileStorageService.storeFile(file);
        user.setPictureUrl(pictureUrl);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        log.info("Updated picture for user: {}", keycloakId);
        return userMapper.toResponse(user);
    }

    @Transactional
    public void updateUserAddress(String keycloakId, AddressUpdateRequest request) {
        User user = getUserOrThrow(keycloakId);
        Address address = Optional.ofNullable(user.getAddress()).orElseGet(Address::new);
        address.setCity(request.city());
        address.setCountry(request.country());
        address.setPostalCode(request.postalCode());
        user.setAddress(address);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        log.info("Updated address for user: {}", keycloakId);
    }



    @Transactional(rollbackFor = Exception.class)
    public UserResponse createUserWithKeycloakSync(UserCreateRequest request) {
        try {
            // Vérifier l'existence de l'utilisateur
            if (userRepository.existsByUsernameOrEmail(request.username(), request.email())) {
                throw new UserAlreadyExistsException("L'utilisateur existe déjà");
            }

            // Création dans Keycloak
            String keycloakId = keycloakAdminService.createUserInKeycloak(request);

            // Création locale
            User user = User.builder()
                    .keycloakId(keycloakId) // Garanti non-null
                    .username(request.username())
                    .email(request.email())
                    .firstName(request.firstName())
                    .lastName(request.lastName())
                    .phoneNumber(request.phoneNumber())
                    .roles(List.of(request.role()))
                    .build();

            userRepository.save(user);

            return userMapper.toResponse(user);

        } catch (UserAlreadyExistsException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (Exception e) {
            log.error("Échec critique : {}", e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Erreur de création utilisateur",
                    e
            );
        }
    }
    @Transactional
    public UserResponse updateUserWithSync(String keycloakId, UserUpdateRequest request) {
        // 1. Update Keycloak
        keycloakAdminService.updateUserInKeycloak(keycloakId, request);

        // 2. Update local database
        User user = getUserOrThrow(keycloakId);

        if(request.phoneNumber() != null) {
            user.setPhoneNumber(request.phoneNumber());
        }

        Address address = user.getAddress() != null ?
                user.getAddress() : new Address();

        if(request.city() != null) address.setCity(request.city());
        if(request.country() != null) address.setCountry(request.country());
        if(request.postalCode() != null) address.setPostalCode(request.postalCode());

        user.setAddress(address);

        if(request.pictureUrl() != null) {
            user.setPictureUrl(request.pictureUrl());
        }

        userRepository.save(user);

        // 3. Force sync to ensure consistency
        syncSingleUserFromKeycloak(user.getUsername());

        return userMapper.toResponse(user);
    }

    private void syncSingleUserFromKeycloak(String username) {
        UserRepresentation kcUser = keycloakAdminService.getUserByUsername(username);
        synchronizeUserData(kcUser, keycloakAdminService.getUserRoles(kcUser.getId()));
    }

    @Transactional
    public UserResponse updateKeycloakProfile(String keycloakId, KeycloakProfileUpdateRequest request) {
        User user = getUserOrThrow(keycloakId);

        // Mise à jour des champs dans la base locale
        if (request.username() != null) user.setUsername(request.username());
        if (request.firstName() != null) user.setFirstName(request.firstName());
        if (request.lastName() != null) user.setLastName(request.lastName());
        if (request.phoneNumber() != null) user.setPhoneNumber(request.phoneNumber());

        // Synchronisation avec Keycloak
        keycloakAdminService.forceUpdateKeycloakFields(
                keycloakId,
                request.username(),
                request.firstName(),
                request.lastName(),
                request.phoneNumber()
        );

        userRepository.save(user);
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updateLocalProfile(String keycloakId, LocalProfileUpdateRequest request) {
        User user = getUserOrThrow(keycloakId);

        if (request.pictureUrl() != null) {
            user.setPictureUrl(request.pictureUrl());
        }

        if (request.address() != null) {
            Address address = Optional.ofNullable(user.getAddress())
                    .orElseGet(Address::new);
            if (request.address().city() != null) address.setCity(request.address().city());
            if (request.address().country() != null) address.setCountry(request.address().country());
            if (request.address().postalCode() != null) address.setPostalCode(request.address().postalCode());
            user.setAddress(address);
        }

        userRepository.save(user);
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updateProfilePicture(String keycloakId, String newPictureUrl) {
        User user = getUserOrThrow(keycloakId);

        // Mise à jour locale uniquement
        user.setPictureUrl(newPictureUrl);
        userRepository.save(user);

        return userMapper.toResponse(user);
    }


    @Transactional(readOnly = true)
    public UserProfileResponse getCompleteUserProfile(String keycloakId) {
        // 1. Récupération des données locales
        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new UserNotFoundException("Utilisateur non trouvé"));

        // 2. Récupération des données Keycloak
        UserRepresentation kcUser = keycloakAdminService.getUserById(keycloakId);

        // 3. Fusion intelligente des données
        return mergeUserData(user, kcUser);
    }

    private UserProfileResponse mergeUserData(User localUser, UserRepresentation kcUser) {
        // Conversion de l'adresse
        AddressResponse addressResponse = localUser.getAddress() != null
                ? new AddressResponse(
                localUser.getAddress().getCity(),
                localUser.getAddress().getCountry(),
                localUser.getAddress().getPostalCode())
                : null;

        return new UserProfileResponse(
                localUser.getId(),
                localUser.getKeycloakId(),
                kcUser.getUsername(),
                kcUser.getEmail(),
                chooseValue(localUser.getFirstName(), kcUser.getFirstName()),
                chooseValue(localUser.getLastName(), kcUser.getLastName()),
                addressResponse,  // Utilisez addressResponse au lieu de localUser.getAddress()
                localUser.getPictureUrl(),
                Optional.ofNullable(localUser.getPhoneNumber())
                        .orElseGet(() -> extractPhoneFromKeycloak(kcUser)),
                extractRoles(kcUser),
                localUser.getCreatedAt(),
                localUser.getUpdatedAt()
        );
    }

    private String chooseValue(String localValue, String kcValue) {
        return localValue != null ? localValue : kcValue;
    }

    private String extractPhoneFromKeycloak(UserRepresentation kcUser) {
        if (kcUser.getAttributes() != null && kcUser.getAttributes().containsKey("phone-mapper")) {
            return kcUser.getAttributes().get("phone-mapper").get(0);
        }
        return null;
    }

    private List<String> extractRoles(UserRepresentation kcUser) {
        // Implémentation existante de récupération des rôles
        return keycloakAdminService.getUserRoles(kcUser.getId());
    }
}