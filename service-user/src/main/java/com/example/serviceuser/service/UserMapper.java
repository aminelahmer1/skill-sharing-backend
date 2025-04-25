package com.example.serviceuser.service;

import com.example.serviceuser.dto.UserResponse;
import com.example.serviceuser.entity.Address;
import com.example.serviceuser.entity.User;
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
            // Synchronisation explicite du numéro de téléphone
            String phoneNumber = kcUser.getAttributes().getOrDefault(PHONE_ATTRIBUTE, List.of())
                    .stream().findFirst().orElse(null);
            if (phoneNumber != null) {
                user.setPhoneNumber(phoneNumber);
                log.debug("Updated phone number for user {}: {}", kcUser.getUsername(), phoneNumber);
            }

            // Mise à jour de l'adresse
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
        log.info("Mapping user to UserResponse: {}", user.getKeycloakId());
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
                user.getPhoneNumber()
        );
    }
}