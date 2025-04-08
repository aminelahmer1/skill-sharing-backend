package com.example.serviceuser.configuration;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String ROLES_CLAIM = "realm_access";
    private static final String ROLES_FIELD = "roles";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        var realmAccess = jwt.getClaimAsMap(ROLES_CLAIM);
        if (realmAccess == null || !realmAccess.containsKey(ROLES_FIELD)) {
            return Collections.emptyList();
        }

        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) realmAccess.get(ROLES_FIELD);

        return roles.stream()
                .map(role -> "ROLE_" + role) // Prefix for Spring Security compatibility
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }
}
