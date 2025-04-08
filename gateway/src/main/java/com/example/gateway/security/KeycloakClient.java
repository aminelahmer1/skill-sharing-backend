package com.example.gateway.security;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(
        name = "keycloak",
        url = "${keycloak.auth-server-url}",
        configuration = KeycloakClientConfig.class
)
public interface KeycloakClient {

    @PostMapping(
            path = "/realms/skill-sharing/protocol/openid-connect/token",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
    )
    ResponseEntity<Map<String, Object>> getToken(@RequestBody MultiValueMap<String, String> params);
}
