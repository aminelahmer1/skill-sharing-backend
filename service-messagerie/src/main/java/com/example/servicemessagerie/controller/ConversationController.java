package com.example.servicemessagerie.controller;

import com.example.servicemessagerie.dto.*;
import com.example.servicemessagerie.service.ConversationService;
import com.example.servicemessagerie.util.UserIdResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final UserIdResolver userIdResolver;

    /**
     * ✅ AMÉLIORÉ: Récupère toutes les conversations d'un utilisateur avec logs détaillés
     */
    @GetMapping("/conversations")
    public ResponseEntity<Page<ConversationDTO>> getUserConversations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader("Authorization") String token,
            @AuthenticationPrincipal Jwt jwt) {

        try {
            Long userId = userIdResolver.resolveUserId(jwt, token);
            log.info("📋 Fetching conversations for resolved user ID: {}", userId);
            log.debug("📋 JWT subject: {}, Resolved ID: {}, Page: {}, Size: {}",
                    jwt.getSubject(), userId, page, size);

            if (page == 0) {
                conversationService.diagnoseUserConversations(userId);
            }

            Page<ConversationDTO> conversations = conversationService.getUserConversations(userId, page, size);

            log.info("✅ Found {} conversations for user {} (page {}/{})",
                    conversations.getTotalElements(), userId, page, conversations.getTotalPages());

            if (conversations.hasContent()) {
                conversations.getContent().forEach(conv -> {
                    log.debug("  📋 Conversation {}: {}, type: {}, participants: {}, unread: {}",
                            conv.getId(), conv.getName(), conv.getType(),
                            conv.getParticipants() != null ? conv.getParticipants().size() : 0,
                            conv.getUnreadCount());
                });
            } else {
                log.warn("⚠️ No conversations found for user {}", userId);
            }

            return ResponseEntity.ok(conversations);

        } catch (Exception e) {
            log.error("❌ Error fetching conversations: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * ✅ NOUVEAU: Endpoint pour récupérer les utilisateurs disponibles selon le type de conversation
     */
    @GetMapping("/available-users")
    public ResponseEntity<List<UserResponse>> getAvailableUsers(
            @RequestParam String conversationType,
            @RequestParam(required = false) Integer skillId,
            @RequestHeader("Authorization") String token,
            @AuthenticationPrincipal Jwt jwt) {

        try {
            Long userId = userIdResolver.resolveUserId(jwt, token);
            log.info("🔍 Getting available users for type: {}, skillId: {}, user: {}",
                    conversationType, skillId, userId);

            List<UserResponse> users = conversationService.getAvailableUsersForConversation(
                    conversationType, skillId, token, userId);

            log.info("✅ Found {} available users for conversation type: {}", users.size(), conversationType);
            return ResponseEntity.ok(users);

        } catch (Exception e) {
            log.error("❌ Error getting available users: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * ✅ AMÉLIORÉ: Crée ou récupère une conversation directe avec validation renforcée
     */
    @PostMapping("/conversations/direct")
    public ResponseEntity<ConversationDTO> createDirectConversation(
            @RequestBody CreateDirectConversationRequest request,
            @RequestHeader("Authorization") String token,
            @AuthenticationPrincipal Jwt jwt) {

        try {
            Long currentUserId = userIdResolver.resolveUserId(jwt, token);
            log.info("💬 Creating direct conversation between {} and {}", currentUserId, request.getOtherUserId());

            // Validation simplifiée
            if (currentUserId.equals(request.getOtherUserId())) {
                log.warn("⚠️ User {} trying to create conversation with themselves", currentUserId);
                return ResponseEntity.badRequest().build();
            }

            ConversationDTO conversation = conversationService.createOrGetDirectConversation(
                    currentUserId, request.getOtherUserId(), token);

            log.info("✅ Direct conversation created/retrieved: {}", conversation.getId());
            return ResponseEntity.ok(conversation);

        } catch (IllegalArgumentException e) {
            log.error("❌ Invalid request for direct conversation: {}", e.getMessage());
            if (e.getMessage().contains("not connected")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .header("X-Error-Message", "Users are not connected")
                        .build();
            }
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("❌ Error creating direct conversation: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * ✅ AMÉLIORÉ: Crée ou récupère une conversation pour une compétence avec validation renforcée
     */
    @PostMapping("/conversations/skill")
    public ResponseEntity<ConversationDTO> createSkillConversation(
            @RequestBody CreateSkillConversationRequest request,
            @RequestHeader("Authorization") String token,
            @AuthenticationPrincipal Jwt jwt) {

        try {
            Long userId = userIdResolver.resolveUserId(jwt, token);
            log.info("🎓 Creating skill conversation for skill {} by user {}", request.getSkillId(), userId);

            // Validation basique uniquement
            if (request.getSkillId() == null || request.getSkillId() <= 0) {
                log.warn("⚠️ Invalid skill ID: {}", request.getSkillId());
                return ResponseEntity.badRequest().build();
            }

            ConversationDTO conversation = conversationService.createOrGetSkillConversation(
                    request.getSkillId(), userId, token);

            log.info("✅ Skill conversation created/retrieved: {} for skill {}",
                    conversation.getId(), request.getSkillId());
            return ResponseEntity.ok(conversation);

        } catch (IllegalArgumentException e) {
            log.error("❌ Invalid skill conversation request: {}", e.getMessage());
            if (e.getMessage().contains("not authorized")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .header("X-Error-Message", "User not authorized for this skill")
                        .build();
            }
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("❌ Error creating skill conversation: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * ✅ AMÉLIORÉ: Crée une conversation de groupe avec validation des participants
     */
    @PostMapping("/conversations/group")
    public ResponseEntity<ConversationDTO> createGroupConversation(
            @RequestBody CreateConversationRequest request,
            @RequestHeader("Authorization") String token,
            @AuthenticationPrincipal Jwt jwt) {

        try {
            Long userId = userIdResolver.resolveUserId(jwt, token);
            log.info("👥 Creating group conversation '{}' by user {}", request.getName(), userId);

            ConversationDTO conversation = conversationService.createGroupConversation(request, userId, token);

            log.info("✅ Group conversation created: {}", conversation.getId());
            return ResponseEntity.ok(conversation);
        } catch (IllegalArgumentException e) {
            log.error("❌ Invalid group conversation request: {}", e.getMessage());
            if (e.getMessage().contains("cannot be added")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .header("X-Error-Message", "Some participants cannot be added to this group")
                        .build();
            }
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("❌ Error creating group conversation: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Récupère une conversation spécifique
     */
    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<ConversationDTO> getConversation(
            @PathVariable Long conversationId,
            @RequestHeader("Authorization") String token,
            @AuthenticationPrincipal Jwt jwt) {

        try {
            Long userId = userIdResolver.resolveUserId(jwt, token);
            log.info("📋 Fetching conversation {} for user {}", conversationId, userId);

            ConversationDTO conversation = conversationService.getConversation(conversationId, userId);

            log.info("✅ Conversation {} retrieved successfully", conversationId);
            return ResponseEntity.ok(conversation);
        } catch (IllegalArgumentException e) {
            log.warn("⚠️ Conversation {} not found", conversationId);
            return ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            log.warn("🚫 User access denied to conversation {}", conversationId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            log.error("❌ Error fetching conversation {}: {}", conversationId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Rechercher conversations
     */
    @GetMapping("/conversations/search")
    public ResponseEntity<List<ConversationDTO>> searchConversations(
            @RequestParam String query,
            @RequestHeader("Authorization") String token,
            @AuthenticationPrincipal Jwt jwt) {

        try {
            Long userId = userIdResolver.resolveUserId(jwt, token);
            log.info("🔍 Searching conversations for user {} with query: '{}'", userId, query);

            List<ConversationDTO> conversations = conversationService.searchConversations(userId, query);

            log.info("✅ Found {} conversations matching query '{}'", conversations.size(), query);
            return ResponseEntity.ok(conversations);
        } catch (Exception e) {
            log.error("❌ Error searching conversations: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Archive une conversation
     */
    @PutMapping("/conversations/{conversationId}/archive")
    public ResponseEntity<Void> archiveConversation(
            @PathVariable Long conversationId,
            @RequestHeader("Authorization") String token,
            @AuthenticationPrincipal Jwt jwt) {

        try {
            Long userId = userIdResolver.resolveUserId(jwt, token);
            log.info("📁 Archiving conversation {} for user {}", conversationId, userId);

            conversationService.archiveConversation(conversationId, userId);

            log.info("✅ Conversation {} archived successfully", conversationId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            log.warn("⚠️ Conversation {} not found for archival", conversationId);
            return ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            log.warn("🚫 User access denied for archiving conversation {}", conversationId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            log.error("❌ Error archiving conversation {}: {}", conversationId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Récupère le nombre total de messages non lus
     */
    @GetMapping("/unread-count")
    public ResponseEntity<Integer> getUnreadCount(
            @RequestHeader("Authorization") String token,
            @AuthenticationPrincipal Jwt jwt) {

        try {
            Long userId = userIdResolver.resolveUserId(jwt, token);
            log.debug("📊 Fetching unread count for user {}", userId);

            int count = conversationService.getUnreadCount(userId);

            log.debug("✅ Unread count for user {}: {}", userId, count);
            return ResponseEntity.ok(count);
        } catch (Exception e) {
            log.error("❌ Error fetching unread count: {}", e.getMessage(), e);
            return ResponseEntity.ok(0); // Retourner 0 au lieu d'une erreur
        }
    }

    /**
     * Récupère le nombre de messages non lus par conversation
     */
    @GetMapping("/unread-per-conversation")
    public ResponseEntity<Map<Long, Integer>> getUnreadCountPerConversation(
            @RequestHeader("Authorization") String token,
            @AuthenticationPrincipal Jwt jwt) {

        try {
            Long userId = userIdResolver.resolveUserId(jwt, token);
            log.debug("📊 Fetching unread counts per conversation for user {}", userId);

            Map<Long, Integer> counts = conversationService.getUnreadCountPerConversation(userId);

            log.debug("✅ Unread counts per conversation for user {}: {} conversations", userId, counts.size());
            return ResponseEntity.ok(counts);
        } catch (Exception e) {
            log.error("❌ Error fetching unread counts: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of());
        }
    }

    /**
     * ✅ NOUVEAU: Endpoint de debug pour diagnostiquer les conversations d'un utilisateur
     */
    @GetMapping("/debug/user-conversations")
    public ResponseEntity<Map<String, Object>> debugUserConversations(
            @RequestHeader("Authorization") String token,
            @AuthenticationPrincipal Jwt jwt) {

        try {
            Long userId = userIdResolver.resolveUserId(jwt, token);
            log.info("🔍 DEBUG: Diagnosing conversations for user {}", userId);

            // Appeler le diagnostic
            conversationService.diagnoseUserConversations(userId);

            // Retourner des informations de debug
            Map<String, Object> debug = new HashMap<>();
            debug.put("userId", userId);
            debug.put("keycloakId", jwt.getSubject());
            debug.put("timestamp", LocalDateTime.now());
            debug.put("message", "Diagnostic completed - check logs");

            return ResponseEntity.ok(debug);

        } catch (Exception e) {
            log.error("❌ Error in debug endpoint: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Vérifie si un utilisateur peut créer une conversation avec un autre
     */
    /**
     * Vérifie si un utilisateur peut créer une conversation avec un autre
     */
    @GetMapping("/conversations/can-create")
    public ResponseEntity<Boolean> canCreateConversation(
            @RequestParam Long otherUserId,
            @RequestHeader("Authorization") String token,
            @AuthenticationPrincipal Jwt jwt) {

        try {
            Long currentUserId = userIdResolver.resolveUserId(jwt, token);
            log.debug("🔍 Checking if user {} can create conversation with user {}", currentUserId, otherUserId);

            // Logique de vérification - pour le moment, toujours autorisé sauf avec soi-même
            boolean canCreate = !currentUserId.equals(otherUserId);

            log.debug("✅ Can create conversation: {}", canCreate);
            return ResponseEntity.ok(canCreate);
        } catch (Exception e) {
            log.error("❌ Error checking conversation creation permission: {}", e.getMessage(), e);
            return ResponseEntity.ok(false);
        }
    }

    /**
     * ✅ NOUVEAU: Endpoint pour valider l'accès à une compétence
     */
    @GetMapping("/conversations/skill/{skillId}/can-access")
    public ResponseEntity<Map<String, Object>> canAccessSkillConversation(
            @PathVariable Integer skillId,
            @RequestHeader("Authorization") String token,
            @AuthenticationPrincipal Jwt jwt) {

        try {
            Long userId = userIdResolver.resolveUserId(jwt, token);
            log.debug("🔍 Checking skill access for user {} and skill {}", userId, skillId);

            // Récupérer les utilisateurs autorisés pour cette compétence
            List<UserResponse> authorizedUsers = conversationService.getAvailableUsersForConversation(
                    "skill", skillId, token, userId);

            boolean hasAccess = authorizedUsers.stream()
                    .anyMatch(user -> user.id().equals(userId));

            Map<String, Object> response = new HashMap<>();
            response.put("hasAccess", hasAccess);
            response.put("skillId", skillId);
            response.put("userId", userId);
            response.put("authorizedUsersCount", authorizedUsers.size());

            log.debug("✅ Skill access check result: {}", hasAccess);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Error checking skill access: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("hasAccess", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.ok(errorResponse);
        }
    }

    /**
     * ✅ NOUVEAU: Endpoint pour obtenir le rôle de l'utilisateur connecté
     */
    @GetMapping("/user/role")
    public ResponseEntity<Map<String, Object>> getCurrentUserRole(
            @RequestHeader("Authorization") String token,
            @AuthenticationPrincipal Jwt jwt) {

        try {
            Long userId = userIdResolver.resolveUserId(jwt, token);

            // Récupérer les rôles depuis le JWT
            List<String> realmRoles = jwt.getClaimAsStringList("roles");
            if (realmRoles == null) {
                // Fallback: essayer avec realm_access
                Map<String, Object> realmAccess = jwt.getClaim("realm_access");
                if (realmAccess != null) {
                    realmRoles = (List<String>) realmAccess.get("roles");
                }
            }

            String primaryRole = "USER"; // Par défaut
            if (realmRoles != null) {
                if (realmRoles.contains("PRODUCER")) {
                    primaryRole = "PRODUCER";
                } else if (realmRoles.contains("RECEIVER")) {
                    primaryRole = "RECEIVER";
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("userId", userId);
            response.put("primaryRole", primaryRole);
            response.put("allRoles", realmRoles != null ? realmRoles : List.of());
            response.put("keycloakId", jwt.getSubject());

            log.debug("✅ User role info: userId={}, primaryRole={}", userId, primaryRole);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Error getting user role: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * ✅ NOUVEAU: Endpoint pour obtenir les statistiques des conversations
     */
    @GetMapping("/conversations/stats")
    public ResponseEntity<Map<String, Object>> getConversationStats(
            @RequestHeader("Authorization") String token,
            @AuthenticationPrincipal Jwt jwt) {

        try {
            Long userId = userIdResolver.resolveUserId(jwt, token);
            log.debug("📊 Getting conversation stats for user {}", userId);

            // Récupérer toutes les conversations de l'utilisateur
            Page<ConversationDTO> conversations = conversationService.getUserConversations(userId, 0, 1000);

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalConversations", conversations.getTotalElements());

            // Statistiques par type
            Map<String, Long> byType = conversations.getContent().stream()
                    .collect(Collectors.groupingBy(
                            ConversationDTO::getType,
                            Collectors.counting()
                    ));
            stats.put("byType", byType);

            // Conversations avec messages non lus
            long unreadConversations = conversations.getContent().stream()
                    .filter(conv -> conv.getUnreadCount() > 0)
                    .count();
            stats.put("unreadConversations", unreadConversations);

            // Total des messages non lus
            int totalUnread = conversationService.getUnreadCount(userId);
            stats.put("totalUnreadMessages", totalUnread);

            // Conversations actives (avec activité récente)
            long activeConversations = conversations.getContent().stream()
                    .filter(conv -> conv.getLastMessageTime() != null &&
                            conv.getLastMessageTime().isAfter(LocalDateTime.now().minusDays(7)))
                    .count();
            stats.put("activeConversations", activeConversations);

            log.debug("✅ Conversation stats computed for user {}", userId);
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("❌ Error getting conversation stats: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }



}