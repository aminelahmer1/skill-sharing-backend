package com.example.gateway.security;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
    private static final String ROLES_CLAIM = "realm_access";
    private static final String ROLES_FIELD = "roles";

    @NotNull
    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        var realmAccess = jwt.getClaimAsMap(ROLES_CLAIM);
        if (realmAccess == null || !realmAccess.containsKey(ROLES_FIELD)) {
            return Collections.emptyList();
        }

        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) realmAccess.get(ROLES_FIELD);

        return roles.stream()
                .map(role -> "ROLE_" + role)  // Préfixe obligatoire pour Spring Security
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }
}
