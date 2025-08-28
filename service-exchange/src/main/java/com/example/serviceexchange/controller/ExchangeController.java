package com.example.serviceexchange.controller;

import com.example.serviceexchange.dto.*;
import com.example.serviceexchange.service.ExchangeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/exchanges")
@RequiredArgsConstructor
public class ExchangeController {
    private final ExchangeService exchangeService;
    @GetMapping("/skill/{skillId}/accepted-receivers")
    @PreAuthorize("hasRole('PRODUCER')")
    public ResponseEntity<List<UserResponse>> getAcceptedReceiversForSkill(
            @PathVariable Integer skillId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(exchangeService.getAcceptedReceiversForSkill(skillId, jwt));
    }
    @GetMapping("/accepted-skills")
    public List<SkillResponse> getAcceptedSkillsForReceiver(@AuthenticationPrincipal Jwt jwt) {
        return exchangeService.getAcceptedSkillsForReceiver(jwt);
    }
    @PostMapping
    @PreAuthorize("hasRole('RECEIVER')")
    public ResponseEntity<ExchangeResponse> createExchange(
            @RequestBody @Valid ExchangeRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(exchangeService.createExchange(request, jwt));
    }

    @PutMapping("/{id}/accept")
    @PreAuthorize("hasRole('PRODUCER')")
    public ResponseEntity<ExchangeResponse> acceptExchange(
            @PathVariable Integer id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(exchangeService.acceptExchange(id, jwt));
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasRole('PRODUCER')")
    public ResponseEntity<ExchangeResponse> rejectExchange(
            @PathVariable Integer id,
            @RequestBody(required = false) RejectReasonRequest reasonRequest,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String reason = reasonRequest != null ? reasonRequest.reason() : null;
        return ResponseEntity.ok(exchangeService.rejectExchange(id, reason, jwt));
    }
    record RejectReasonRequest(String reason) {}
//    @PostMapping("/{skillId}/start")
//    @PreAuthorize("hasRole('PRODUCER')")
//    public ResponseEntity<Void> startSession(
//            @PathVariable Integer skillId,
//            @AuthenticationPrincipal Jwt jwt
//    ) {
//        exchangeService.startSession(skillId, jwt);
//        return ResponseEntity.ok().build();
//    }
//
//    @PostMapping("/{skillId}/complete")
//    @PreAuthorize("hasRole('PRODUCER')")
//    public ResponseEntity<Void> completeSession(
//            @PathVariable Integer skillId,
//            @AuthenticationPrincipal Jwt jwt
//    ) {
//        exchangeService.completeSession(skillId, jwt);
//        return ResponseEntity.ok().build();
//    }

    @GetMapping("/my-exchanges")
    public ResponseEntity<List<ExchangeResponse>> getUserExchanges(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(exchangeService.getUserExchanges(jwt));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('PRODUCER')")
    public ResponseEntity<List<ExchangeResponse>> getPendingExchanges(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(exchangeService.getPendingExchangesForProducer(jwt));
    }

    @PutMapping("/{skillId}/accept-all")
    @PreAuthorize("hasRole('PRODUCER')")
    public ResponseEntity<List<ExchangeResponse>> acceptAllPendingExchanges(
            @PathVariable Integer skillId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(exchangeService.acceptAllPendingExchanges(skillId, jwt));
    }
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('PRODUCER')")
    public ResponseEntity<Void> updateExchangeStatus(
            @PathVariable Integer id,
            @RequestParam String status,
            @AuthenticationPrincipal Jwt jwt
    ) {
        exchangeService.updateStatus(id, status, jwt);
        return ResponseEntity.ok().build();
    }
    @GetMapping("/skill/{skillId}")
    @PreAuthorize("hasAnyRole('PRODUCER', 'RECEIVER')")
    public ResponseEntity<List<ExchangeResponse>> getExchangesBySkillId(
            @PathVariable Integer skillId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(exchangeService.getExchangesBySkillId(skillId, jwt));
    }
    @GetMapping("/producer/subscribers")
    @PreAuthorize("hasRole('PRODUCER')")
    public ResponseEntity<List<UserResponse>> getAllSubscribersForProducer(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(exchangeService.getAllSubscribersForProducer(jwt));
    }
    @GetMapping("/producer/subscribers/detailed")
    @PreAuthorize("hasRole('PRODUCER')")
    public ResponseEntity<List<SubscriberDetailResponse>> getDetailedSubscribersForProducer(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(exchangeService.getDetailedSubscribersForProducer(jwt));
    }
    @GetMapping("/receiver/peers")
    @PreAuthorize("hasRole('RECEIVER')")
    public ResponseEntity<List<UserResponse>> getPeerReceiversForReceiver(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(exchangeService.getPeerReceiversForReceiver(jwt));
    }

    // Version détaillée avec informations sur les compétences communes
    @GetMapping("/receiver/peers/detailed")
    @PreAuthorize("hasRole('RECEIVER')")
    public ResponseEntity<List<PeerReceiverDetailResponse>> getDetailedPeerReceiversForReceiver(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(exchangeService.getDetailedPeerReceiversForReceiver(jwt));
    }
    @GetMapping("/receiver/community/members")
    @PreAuthorize("hasRole('RECEIVER')")
    public ResponseEntity<List<CommunityMemberResponse>> getAllCommunityMembersForReceiver(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(exchangeService.getAllCommunityMembersForReceiver(jwt));
    }
    @GetMapping("/skill/{skillId}/users")
    @PreAuthorize("hasAnyRole('PRODUCER', 'RECEIVER')")
    public ResponseEntity<SkillUsersResponse> getSkillUsers(
            @PathVariable Integer skillId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(exchangeService.getSkillUsers(skillId, jwt));
    }

    // Version simplifiée qui retourne juste la liste des utilisateurs
    @GetMapping("/skill/{skillId}/users/simple")
    @PreAuthorize("hasAnyRole('PRODUCER', 'RECEIVER')")
    public ResponseEntity<List<UserResponse>> getSkillUsersSimple(
            @PathVariable Integer skillId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(exchangeService.getSkillUsersSimple(skillId, jwt));
    }
// Ajouter ces endpoints dans ExchangeController.java (URLs modifiées pour éviter les conflits)

    /**
     * Récupère toutes les compétences de l'utilisateur connecté avec leurs utilisateurs
     * - PRODUCER: retourne toutes ses compétences avec les receivers inscrits
     * - RECEIVER: retourne toutes ses compétences avec producteurs et autres receivers
     */
    @GetMapping("/my-skills/users")
    @PreAuthorize("hasAnyRole('PRODUCER', 'RECEIVER')")
    public ResponseEntity<UserSkillsWithUsersResponse> getAllMySkillsWithUsers(
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(exchangeService.getAllUserSkillsWithUsers(jwt));
    }

    /**
     * Version simplifiée qui retourne juste la liste de tous les utilisateurs
     * de toutes les compétences de l'utilisateur connecté
     */
    @GetMapping("/my-skills/users/simple")
    @PreAuthorize("hasAnyRole('PRODUCER', 'RECEIVER')")
    public ResponseEntity<List<UserResponse>> getAllMySkillUsersSimple(
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(exchangeService.getAllSkillUsersSimple(jwt));
    }

    /**
     * Récupère les statistiques globales des compétences de l'utilisateur
     */
    @GetMapping("/my-skills/stats")
    @PreAuthorize("hasAnyRole('PRODUCER', 'RECEIVER')")
    public ResponseEntity<UserSkillsStats> getMySkillsStats(
            @AuthenticationPrincipal Jwt jwt) {
        UserSkillsWithUsersResponse response = exchangeService.getAllUserSkillsWithUsers(jwt);
        return ResponseEntity.ok(response.globalStats());
    }

    /**
     * Récupère une compétence spécifique avec ses utilisateurs (NOUVELLE LOGIQUE)
     * Vérifie que l'utilisateur a accès à cette compétence via ses abonnements
     * URL: /skill/{skillId}/users/detailed (pour éviter conflit avec l'ancien endpoint)
     */
    @GetMapping("/skill/{skillId}/users/detailed")
    @PreAuthorize("hasAnyRole('PRODUCER', 'RECEIVER')")
    public ResponseEntity<SkillWithUsersResponse> getSkillUsersDetailed(
            @PathVariable Integer skillId,
            @AuthenticationPrincipal Jwt jwt) {

        // Récupérer toutes les compétences de l'utilisateur
        UserSkillsWithUsersResponse allSkills = exchangeService.getAllUserSkillsWithUsers(jwt);

        // Chercher la compétence spécifique
        Optional<SkillWithUsersResponse> specificSkill = allSkills.skills().stream()
                .filter(skill -> skill.skillId().equals(skillId))
                .findFirst();

        if (specificSkill.isEmpty()) {
            throw new AccessDeniedException("You don't have access to this skill or it doesn't exist");
        }

        return ResponseEntity.ok(specificSkill.get());
    }

    /**
     * Version simplifiée pour une compétence spécifique (NOUVELLE LOGIQUE)
     * URL: /skill/{skillId}/users/new-simple (pour éviter conflit avec l'ancien endpoint)
     */
    @GetMapping("/skill/{skillId}/users/new-simple")
    @PreAuthorize("hasAnyRole('PRODUCER', 'RECEIVER')")
    public ResponseEntity<List<UserResponse>> getSkillUsersNewSimple(
            @PathVariable Integer skillId,
            @AuthenticationPrincipal Jwt jwt) {

        SkillWithUsersResponse skillUsers = getSkillUsersDetailed(skillId, jwt).getBody();
        if (skillUsers == null) {
            return ResponseEntity.notFound().build();
        }

        List<UserResponse> allUsers = new ArrayList<>();

        // Ajouter le producteur seulement si l'utilisateur courant est un receiver
        if ("RECEIVER".equals(skillUsers.userRole())) {
            allUsers.add(skillUsers.skillProducer());
        }

        // Ajouter tous les receivers
        allUsers.addAll(skillUsers.receivers());

        return ResponseEntity.ok(allUsers);
    }
    @GetMapping("/producer/my-skills/users")
    @PreAuthorize("hasRole('PRODUCER')")
    public ResponseEntity<UserSkillsWithUsersResponse> getProducerSkillsWithUsers(
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(exchangeService.getAllUserSkillsWithUsers(jwt));
    }

    @DeleteMapping("/skill/{skillId}/cleanup")
    @PreAuthorize("hasRole('PRODUCER')")
    public ResponseEntity<Void> deleteExchangesBySkillId(
            @PathVariable Integer skillId,
            @AuthenticationPrincipal Jwt jwt) {
        exchangeService.deleteAllExchangesBySkillId(skillId, jwt);
        return ResponseEntity.noContent().build();
    }

    /**
     * Vérifie s'il existe des exchanges pour un skill donné
     */
    @GetMapping("/skill/{skillId}/exists")
    @PreAuthorize("hasAnyRole('PRODUCER', 'RECEIVER')")
    public ResponseEntity<Boolean> hasExchangesForSkill(
            @PathVariable Integer skillId,
            @AuthenticationPrincipal Jwt jwt) {
        boolean exists = exchangeService.hasExchangesForSkill(skillId);
        return ResponseEntity.ok(exists);
    }

    /**
     * Endpoint admin pour nettoyer les exchanges orphelins au démarrage
     * Peut être appelé manuellement ou automatiquement au startup
     */
    @PostMapping("/admin/cleanup-orphaned")
    @PreAuthorize("hasRole('ADMIN')") // Ou utiliser un token système
    public ResponseEntity<Map<String, Object>> cleanupOrphanedExchanges(
            @AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> result = exchangeService.cleanupOrphanedExchanges(jwt);
        return ResponseEntity.ok(result);
    }
}