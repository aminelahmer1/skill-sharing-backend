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

@Slf4j
@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final UserIdResolver userIdResolver;

    /**
     * ✅ CORRIGÉ: Récupère toutes les conversations d'un utilisateur avec logs détaillés
     */
    @GetMapping("/conversations")
    public ResponseEntity<Page<ConversationDTO>> getUserConversations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader("Authorization") String token,
            @AuthenticationPrincipal Jwt jwt) {

        try {
            // Utiliser le resolver pour obtenir le bon ID
            Long userId = userIdResolver.resolveUserId(jwt, token);

            // Logger pour debug avec plus de détails
            log.info("📋 Fetching conversations for resolved user ID: {}", userId);
            log.debug("📋 JWT subject: {}, Resolved ID: {}, Page: {}, Size: {}",
                    jwt.getSubject(), userId, page, size);

            // ✅ AJOUT: Vérifier si c'est le premier appel et faire un diagnostic
            if (page == 0) {
                conversationService.diagnoseUserConversations(userId);
            }

            Page<ConversationDTO> conversations = conversationService.getUserConversations(userId, page, size);

            log.info("✅ Found {} conversations for user {} (page {}/{})",
                    conversations.getTotalElements(), userId, page, conversations.getTotalPages());

            // ✅ AJOUT: Log détaillé des conversations trouvées
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
     * ✅ CORRIGÉ: Crée ou récupère une conversation directe avec validation améliorée
     */
    @PostMapping("/conversations/direct")
    public ResponseEntity<ConversationDTO> createDirectConversation(
            @RequestBody CreateDirectConversationRequest request,
            @RequestHeader("Authorization") String token,
            @AuthenticationPrincipal Jwt jwt) {

        try {
            Long currentUserId = userIdResolver.resolveUserId(jwt, token);
            log.info("💬 Creating direct conversation between {} and {}", currentUserId, request.getOtherUserId());

            // ✅ : Validation simplifiée
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
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("❌ Error creating direct conversation: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * ✅ CORRIGÉ: Crée ou récupère une conversation pour une compétence avec validation améliorée
     */
    @PostMapping("/conversations/skill")
    public ResponseEntity<ConversationDTO> createSkillConversation(
            @RequestBody CreateSkillConversationRequest request,
            @RequestHeader("Authorization") String token,
            @AuthenticationPrincipal Jwt jwt) {

        try {
            Long userId = userIdResolver.resolveUserId(jwt, token);
            log.info("🎓 Creating skill conversation for skill {} by user {}", request.getSkillId(), userId);

            // ✅ : Validation basique uniquement
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
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("❌ Error creating skill conversation: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Crée une conversation de groupe
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
        } catch (Exception e) {
            log.error("❌ Error creating group conversation: {}", e.getMessage(), e);
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
     * Récupère les conversations par type
     */
    @GetMapping("/conversations/by-type")
    public ResponseEntity<List<ConversationDTO>> getConversationsByType(
            @RequestParam String type,
            @RequestHeader("Authorization") String token,
            @AuthenticationPrincipal Jwt jwt) {

        try {
            Long userId = userIdResolver.resolveUserId(jwt, token);
            log.info("📋 Fetching conversations by type '{}' for user {}", type, userId);

            // TODO: Implémenter dans le service
            return ResponseEntity.ok(List.of()); // Placeholder
        } catch (Exception e) {
            log.error("❌ Error fetching conversations by type: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

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
     * DTO pour création de conversation directe
     */
    public static class CreateDirectConversationRequest {
        private Long currentUserId;
        private Long otherUserId;

        // Getters et setters
        public Long getCurrentUserId() { return currentUserId; }
        public void setCurrentUserId(Long currentUserId) { this.currentUserId = currentUserId; }
        public Long getOtherUserId() { return otherUserId; }
        public void setOtherUserId(Long otherUserId) { this.otherUserId = otherUserId; }
    }

    /**
     * DTO pour création de conversation de compétence
     */
    public static class CreateSkillConversationRequest {
        private Integer skillId;

        // Getters et setters
        public Integer getSkillId() { return skillId; }
        public void setSkillId(Integer skillId) { this.skillId = skillId; }
    }
}