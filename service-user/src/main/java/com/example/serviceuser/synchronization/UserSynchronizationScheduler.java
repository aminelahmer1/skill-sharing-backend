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
     * Scheduled user synchronization with Keycloak every hour.
     */
    @Scheduled(fixedRate = 6000)
    public void scheduleUserSynchronization() {
        log.info("Scheduled synchronization with Keycloak started...");
        userService.syncUsersWithKeycloak();
        log.info("Scheduled synchronization with Keycloak completed.");
    }
}
