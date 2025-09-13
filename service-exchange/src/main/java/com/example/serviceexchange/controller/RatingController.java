package com.example.serviceexchange.controller;

import com.example.serviceexchange.FeignClient.UserServiceClient;
import com.example.serviceexchange.dto.*;
import com.example.serviceexchange.service.RatingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@Slf4j
@RestController
@RequestMapping("/api/v1/exchanges/ratings")
@RequiredArgsConstructor
public class RatingController {

    private final RatingService ratingService;
private final UserServiceClient userServiceClient;
    /**
     * Soumettre un rating pour un échange complété
     */
    @PostMapping("/{exchangeId}")
    @PreAuthorize("hasRole('RECEIVER')")
    public ResponseEntity<RatingResponse> submitRating(
            @PathVariable Integer exchangeId,
            @RequestBody @Valid RatingRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        RatingResponse response = ratingService.submitRating(exchangeId, request, jwt);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Mettre à jour un rating existant
     */
    @PutMapping("/{exchangeId}")
    @PreAuthorize("hasRole('RECEIVER')")
    public ResponseEntity<RatingResponse> updateRating(
            @PathVariable Integer exchangeId,
            @RequestBody @Valid RatingRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        RatingResponse response = ratingService.submitRating(exchangeId, request, jwt);
        return ResponseEntity.ok(response);
    }

    /**
     * Récupérer le rating d'un échange spécifique
     */
    @GetMapping("/{exchangeId}")
    @PreAuthorize("hasAnyRole('PRODUCER', 'RECEIVER')")
    public ResponseEntity<RatingResponse> getRatingForExchange(
            @PathVariable Integer exchangeId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        RatingResponse response = ratingService.getRatingForExchange(exchangeId, jwt);
        return response != null ? ResponseEntity.ok(response) : ResponseEntity.noContent().build();
    }

    /**
     * Récupérer les statistiques de rating d'un producteur
     */
    @GetMapping("/producer/{producerId}/stats")
    public ResponseEntity<ProducerRatingStats> getProducerRatingStats(
            @PathVariable Long producerId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        ProducerRatingStats stats = ratingService.getProducerRatingStats(producerId, jwt);
        return ResponseEntity.ok(stats);
    }

    /**
     * Récupérer ses propres statistiques de rating (pour le producteur connecté)
     */
    @GetMapping("/my-stats")
    @PreAuthorize("hasRole('PRODUCER')")
    public ResponseEntity<ProducerRatingStats> getMyRatingStats(
            @AuthenticationPrincipal Jwt jwt
    ) {
        try {
            ProducerRatingStats stats = ratingService.getMyRatingStats(jwt);
            log.info("My rating stats retrieved: average={}, totalRatings={}",
                    stats.averageRating(), stats.totalRatings());
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error getting my rating stats: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    /**
     * Récupérer les statistiques de rating d'une compétence
     */
    @GetMapping("/skill/{skillId}/stats")
    public ResponseEntity<SkillRatingStats> getSkillRatingStats(
            @PathVariable Integer skillId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        SkillRatingStats stats = ratingService.getSkillRatingStats(skillId, jwt);
        return ResponseEntity.ok(stats);
    }

    /**
     * Récupérer tous les échanges complétés non notés du receiver connecté
     */
    @GetMapping("/unrated")
    @PreAuthorize("hasRole('RECEIVER')")
    public ResponseEntity<List<ExchangeResponse>> getUnratedCompletedExchanges(
            @AuthenticationPrincipal Jwt jwt
    ) {
        List<ExchangeResponse> exchanges = ratingService.getUnratedCompletedExchanges(jwt);
        return ResponseEntity.ok(exchanges);
    }

    /**
     * Vérifier si un échange a été noté
     */
    @GetMapping("/{exchangeId}/has-rated")
    @PreAuthorize("hasRole('RECEIVER')")
    public ResponseEntity<Boolean> hasReceiverRated(
            @PathVariable Integer exchangeId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        boolean hasRated = ratingService.hasReceiverRated(exchangeId, jwt);
        return ResponseEntity.ok(hasRated);
    }

    // Helper method - À implémenter selon votre logique
    private Long getUserIdFromKeycloakId(String keycloakId) {
        // Implémenter la logique pour récupérer l'ID utilisateur depuis Keycloak ID
        // Ceci est un placeholder
        return 1L;
    }



/**
 * Récupérer les statistiques complètes du dashboard producteur
 */
    @GetMapping("/producer/dashboard")
    @PreAuthorize("hasRole('PRODUCER')")
    public ResponseEntity<ProducerDashboardStats> getProducerDashboard(
            @AuthenticationPrincipal Jwt jwt
    ) {
        String keycloakId = jwt.getSubject();
        Long producerId = getUserIdFromKeycloakId(keycloakId, jwt);

        ProducerDashboardStats stats = ratingService.getProducerDashboardStats(producerId, jwt);
        return ResponseEntity.ok(stats);
    }

    /**
     * Récupérer les statistiques d'engagement détaillées
     */
    @GetMapping("/producer/engagement")
    @PreAuthorize("hasRole('PRODUCER')")
    public ResponseEntity<ProducerEngagementStats> getProducerEngagement(
            @AuthenticationPrincipal Jwt jwt
    ) {
        String keycloakId = jwt.getSubject();
        Long producerId = getUserIdFromKeycloakId(keycloakId, jwt);

        ProducerEngagementStats stats = ratingService.getProducerEngagementStats(producerId, jwt);
        return ResponseEntity.ok(stats);
    }

    /**
     * Récupérer les statistiques de croissance
     */
    @GetMapping("/producer/growth")
    @PreAuthorize("hasRole('PRODUCER')")
    public ResponseEntity<ProducerGrowthStats> getProducerGrowth(
            @AuthenticationPrincipal Jwt jwt
    ) {
        String keycloakId = jwt.getSubject();
        Long producerId = getUserIdFromKeycloakId(keycloakId, jwt);

        ProducerGrowthStats stats = ratingService.getProducerGrowthStats(producerId, jwt);
        return ResponseEntity.ok(stats);
    }

    /**
     * Récupérer les statistiques de qualité
     */
    @GetMapping("/producer/quality")
    @PreAuthorize("hasRole('PRODUCER')")
    public ResponseEntity<ProducerQualityStats> getProducerQuality(
            @AuthenticationPrincipal Jwt jwt
    ) {
        String keycloakId = jwt.getSubject();
        Long producerId = getUserIdFromKeycloakId(keycloakId, jwt);

        ProducerQualityStats stats = ratingService.getProducerQualityStats(producerId, jwt);
        return ResponseEntity.ok(stats);
    }

    /**
     * Récupérer les données pour graphique d'activité mensuelle
     */
    @GetMapping("/producer/activity/monthly")
    @PreAuthorize("hasRole('PRODUCER')")
    public ResponseEntity<List<MonthlyActivityData>> getMonthlyActivity(
            @AuthenticationPrincipal Jwt jwt
    ) {
        String keycloakId = jwt.getSubject();
        Long producerId = getUserIdFromKeycloakId(keycloakId, jwt);

        ProducerDashboardStats dashboardStats = ratingService.getProducerDashboardStats(producerId, jwt);
        return ResponseEntity.ok(dashboardStats.monthlyActivity());
    }

    /**
     * Récupérer les performances par compétence
     */
    @GetMapping("/producer/skills/performance")
    @PreAuthorize("hasRole('PRODUCER')")
    public ResponseEntity<List<SkillPerformanceData>> getSkillsPerformance(
            @AuthenticationPrincipal Jwt jwt
    ) {
        String keycloakId = jwt.getSubject();
        Long producerId = getUserIdFromKeycloakId(keycloakId, jwt);

        ProducerDashboardStats dashboardStats = ratingService.getProducerDashboardStats(producerId, jwt);
        return ResponseEntity.ok(dashboardStats.skillPerformance());
    }

    /**
     * Récupérer l'évolution des notes
     */
    @GetMapping("/producer/ratings/evolution")
    @PreAuthorize("hasRole('PRODUCER')")
    public ResponseEntity<List<RatingEvolutionData>> getRatingEvolution(
            @AuthenticationPrincipal Jwt jwt
    ) {
        String keycloakId = jwt.getSubject();
        Long producerId = getUserIdFromKeycloakId(keycloakId, jwt);

        ProducerDashboardStats dashboardStats = ratingService.getProducerDashboardStats(producerId, jwt);
        return ResponseEntity.ok(dashboardStats.ratingEvolution());
    }

    // Améliorer la méthode helper existante
    private Long getUserIdFromKeycloakId(String keycloakId, Jwt jwt) {
        try {
            String token = "Bearer " + jwt.getTokenValue();
            UserResponse user = userServiceClient.getUserByKeycloakId(keycloakId, token);
            return user.id();
        } catch (Exception e) {
            log.error("Failed to get user ID from Keycloak ID {}: {}", keycloakId, e.getMessage());
            throw new RuntimeException("Failed to get user information", e);
        }
    }
}