package com.example.serviceuser.config;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

public class JwtAuthentication extends AbstractAuthenticationToken {

    private final String token;

    public JwtAuthentication(String token) {
        super(AuthorityUtils.NO_AUTHORITIES);
        this.token = token;
        setAuthenticated(true); // à ne faire qu'après avoir validé le token !
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    @Override
    public Object getPrincipal() {
        return token; // Vous pouvez retourner un utilisateur ou d'autres informations ici
    }
}
