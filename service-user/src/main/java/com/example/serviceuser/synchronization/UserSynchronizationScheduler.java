package com.example.serviceuser.synchronization;

import com.example.serviceuser.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserSynchronizationScheduler {

    private final UserService userService;

    /**
     * Automatically syncs users from Keycloak every hour.
     */
    @Scheduled(fixedRate = 3600000) // Every 1 hour
    public void scheduleUserSynchronization() {
        log.info("Scheduled synchronization with Keycloak started...");
        userService.syncUsersWithKeycloak();
        log.info("Scheduled synchronization with Keycloak completed.");
    }
}
