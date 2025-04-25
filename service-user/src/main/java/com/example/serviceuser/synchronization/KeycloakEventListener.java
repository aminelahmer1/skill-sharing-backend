package com.example.serviceuser.synchronization;

import com.example.serviceuser.service.UserService;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeycloakEventListener implements EventListenerProvider {

    private static final Logger log = LoggerFactory.getLogger(KeycloakEventListener.class);
    private final UserService userService;

    public KeycloakEventListener(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void onEvent(Event event) {
        if (event.getType() == EventType.REGISTER) { // Triggered for new user registration
            String userId = event.getUserId(); // Fetch the Keycloak user ID
            log.info("New user registered with ID: {}", userId);

            try {
                // Synchronize the new user with the database
                userService.syncSingleUser(userId);
                log.info("Synchronization completed for user ID: {}", userId);
            } catch (Exception e) {
                log.error("Error synchronizing user with ID {}: {}", userId, e.getMessage());
            }
        }
    }

    @Override
    public void onEvent(AdminEvent adminEvent, boolean includeRepresentation) {
        // Handle admin events if necessary (optional)
        log.info("Admin event triggered: {}, Resource Type: {}", adminEvent.getOperationType(), adminEvent.getResourceType());
    }

    @Override
    public void close() {
        log.info("KeycloakEventListener closed.");
        // Cleanup logic if required
    }
}
