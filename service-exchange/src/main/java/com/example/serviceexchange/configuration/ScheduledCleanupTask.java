/*package com.example.serviceexchange.configuration;

import com.example.serviceexchange.service.ExchangeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
@EnableScheduling
@ConditionalOnProperty(name = "cleanup.scheduled.enabled", havingValue = "true")
public class ScheduledCleanupTask {

    private final ExchangeService exchangeService;

    @Scheduled(cron = "${cleanup.scheduled.cron:0 0 2 * * ?}")
    public void performScheduledCleanup() {
        log.info("=== Starting scheduled cleanup task ===");

        try {
            Jwt systemJwt = createSystemJwt();
            Map<String, Object> result = exchangeService.cleanupOrphanedExchanges(systemJwt);

            log.info("Scheduled cleanup completed: {}", result);

        } catch (Exception e) {
            log.error("Scheduled cleanup failed: {}", e.getMessage(), e);
        }

        log.info("=== Scheduled cleanup task completed ===");
    }

    private Jwt createSystemJwt() {
        Map<String, Object> headers = new HashMap<>();
        headers.put("alg", "none");

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "system-scheduler");
        claims.put("roles", List.of("ADMIN", "SYSTEM"));
        claims.put("preferred_username", "system-scheduler");

        return new Jwt(
                "scheduler-token",
                Instant.now(),
                Instant.now().plusSeconds(60),
                headers,
                claims
        );
    }
}*/