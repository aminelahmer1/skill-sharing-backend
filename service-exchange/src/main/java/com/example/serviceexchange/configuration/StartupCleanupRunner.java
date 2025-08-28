/*package com.example.serviceexchange.configuration;

import com.example.serviceexchange.service.ExchangeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
@Profile("!test") // Ne pas exécuter dans les tests
public class StartupCleanupRunner implements ApplicationRunner {

    private final ExchangeService exchangeService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("=== Starting application cleanup process ===");

        try {
            // Créer un JWT système pour les opérations de maintenance
            Jwt systemJwt = createSystemJwt();

            // Nettoyer les exchanges orphelins
            Map<String, Object> cleanupResult = exchangeService.cleanupOrphanedExchanges(systemJwt);

            log.info("Cleanup completed: {}", cleanupResult);

            if ((int) cleanupResult.get("deletedCount") > 0) {
                log.warn("Deleted {} orphaned exchanges for skill IDs: {}",
                        cleanupResult.get("deletedCount"),
                        cleanupResult.get("invalidSkillIds"));
            } else {
                log.info("No orphaned exchanges found - database is clean");
            }

        } catch (Exception e) {
            log.error("Failed to perform startup cleanup: {}", e.getMessage(), e);
            // Ne pas faire échouer le démarrage de l'application
        }

        log.info("=== Startup cleanup process completed ===");
    }


    private Jwt createSystemJwt() {
        Map<String, Object> headers = new HashMap<>();
        headers.put("alg", "none");

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "system");
        claims.put("roles", List.of("ADMIN", "SYSTEM"));
        claims.put("preferred_username", "system");

        return new Jwt(
                "system-token",
                Instant.now(),
                Instant.now().plusSeconds(60),
                headers,
                claims
        );
    }
}

*/