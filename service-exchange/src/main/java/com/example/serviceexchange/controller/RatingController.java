package com.example.serviceexchange.controller;

import com.example.serviceexchange.dto.*;
import com.example.serviceexchange.service.RatingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/exchanges/ratings")
@RequiredArgsConstructor
public class RatingController {

    private final RatingService ratingService;

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
        // Récupérer l'ID du producteur connecté depuis le JWT
        String keycloakId = jwt.getSubject();
        // Note: Vous devrez adapter cette partie selon comment vous récupérez l'ID utilisateur
        // Pour simplifier, on suppose que vous avez une méthode pour obtenir l'ID depuis le Keycloak ID
        Long producerId = getUserIdFromKeycloakId(keycloakId);

        ProducerRatingStats stats = ratingService.getProducerRatingStats(producerId, jwt);
        return ResponseEntity.ok(stats);
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
}