package com.example.serviceuser.synchronization;

import com.example.serviceuser.service.UserService;
import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class KeycloakEventListenerFactory implements EventListenerProviderFactory {

    private UserService userService;
    public KeycloakEventListenerFactory() {
    }

    public KeycloakEventListenerFactory(UserService userService) {
        this.userService = userService;
    }

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        return new KeycloakEventListener(userService); // Pass required dependencies
    }

    @Override
    public String getId() {
        return "custom-event-listener";
    }

    @Override
    public void init(Config.Scope scope) {
    }

    @Override
    public void postInit(KeycloakSessionFactory sessionFactory) {

    }

    @Override
    public void close() {
        // Cleanup if needed
    }
}
