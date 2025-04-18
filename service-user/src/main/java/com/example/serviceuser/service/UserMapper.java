package com.example.serviceuser.service;

import com.example.serviceuser.dto.UpdateProfileRequest;
import com.example.serviceuser.dto.UserResponse;
import com.example.serviceuser.entity.Address;
import com.example.serviceuser.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class UserMapper {

    public void updateFromKeycloak(User user, UserRepresentation kcUser) {
        log.info("Updating user with Keycloak data: {}", kcUser.getId());
        user.setUsername(kcUser.getUsername());
        user.setEmail(kcUser.getEmail());
        user.setFirstName(kcUser.getFirstName());
        user.setLastName(kcUser.getLastName());

        if (kcUser.getAttributes() != null) {
            Address address = new Address();
            address.setStreet(getFirstAttribute(kcUser, "street"));
            address.setLocality(getFirstAttribute(kcUser, "locality"));
            address.setRegion(getFirstAttribute(kcUser, "region"));
            address.setPostalCode(getFirstAttribute(kcUser, "postal_code"));
            address.setCountry(getFirstAttribute(kcUser, "country"));
            user.setAddress(address);
        }

        user.setUpdatedAt(LocalDateTime.now());
    }

    private String getFirstAttribute(UserRepresentation user, String attributeName) {
        return user.getAttributes() != null
                ? user.getAttributes().getOrDefault(attributeName, List.of()).stream().findFirst().orElse(null)
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
                user.getAddress(),
                user.getRoles(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
