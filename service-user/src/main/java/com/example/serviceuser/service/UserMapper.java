package com.example.serviceuser.service;

import com.example.serviceuser.dto.*;
import com.example.serviceuser.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class UserMapper {

    private static final String PHONE_ATTRIBUTE = "phone-mapper";

    public void updateFromKeycloak(User user, UserRepresentation kcUser) {
        log.info("Updating user with Keycloak data: {}", kcUser.getId());
        user.setUsername(kcUser.getUsername());
        user.setEmail(kcUser.getEmail());
        user.setFirstName(kcUser.getFirstName());
        user.setLastName(kcUser.getLastName());

        if (kcUser.getAttributes() != null) {
            String phoneNumber = kcUser.getAttributes().getOrDefault(PHONE_ATTRIBUTE, List.of())
                    .stream().findFirst().orElse(null);
            if (phoneNumber != null) {
                user.setPhoneNumber(phoneNumber);
            }

            // Ajout de la bio depuis Keycloak si présente
            String bio = kcUser.getAttributes().getOrDefault("bio", List.of())
                    .stream().findFirst().orElse(null);
            if (bio != null) {
                user.setBio(bio);
            }

            if (user.getAddress() == null) {
                user.setAddress(new Address());
            }
            Address address = user.getAddress();
            address.setCity(getFirstAttribute(kcUser, "city"));
            address.setCountry(getFirstAttribute(kcUser, "country"));
            address.setPostalCode(getFirstAttribute(kcUser, "postal_code"));
        }

        user.setUpdatedAt(LocalDateTime.now());
    }

    private String getFirstAttribute(UserRepresentation kcUser, String attributeName) {
        return kcUser.getAttributes() != null
                ? kcUser.getAttributes().getOrDefault(attributeName, List.of()).stream().findFirst().orElse(null)
                : null;
    }

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getKeycloakId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getAddress() != null ? user.getAddress().getCity() : null,
                user.getAddress() != null ? user.getAddress().getCountry() : null,
                user.getAddress() != null ? user.getAddress().getPostalCode() : null,
                user.getRoles(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getPictureUrl(),
                user.getBio(),
                user.getPhoneNumber()
        );
    }
    public UserProfileResponse toProfileResponse(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getKeycloakId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getAddress() != null ? new AddressResponse(
                        user.getAddress().getCity(),
                        user.getAddress().getCountry(),
                        user.getAddress().getPostalCode()
                ) : null,
                user.getPictureUrl(),
                user.getBio(),
                user.getPhoneNumber(),
                user.getRoles(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}