package com.example.servicemessagerie.controller;

import com.example.servicemessagerie.dto.*;
import com.example.servicemessagerie.entity.Conversation;
import com.example.servicemessagerie.service.ConversationService;
import com.example.servicemessagerie.service.MessageService;
import com.example.servicemessagerie.util.UserIdResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final ConversationService conversationService;
    private final UserIdResolver userIdResolver;

    /**
     * ✅ CORRIGÉ: Envoi de message avec auto-join pour conversations de compétence
     */
    @PostMapping("/send")
    public ResponseEntity<MessageDTO> sendMessage(
            @RequestBody MessageRequest request,
            @RequestHeader("Authorization") String token,
            @AuthenticationPrincipal Jwt jwt) {

        try {
            Long userId = userIdResolver.resolveUserId(jwt, token);
            request.setSenderId(userId);

            log.info("📤 Sending message from user {} to conversation {}", userId, request.getConversationId());
            log.debug("Message content: '{}', type: {}", request.getContent(), request.getType());

            // ✅ : Vérifier que l'utilisateur peut envoyer
            Conversation conversation = conversationService.getConversationEntity(request.getConversationId());
            if (conversation == null) {
                log.error("❌ Conversation {} not found", request.getConversationId());
                return ResponseEntity.notFound().build();
            }

            boolean canSend = conversationService.canUserSendMessage(conversation, userId);
            log.info("Can user {} send to conversation {}? {}", userId, request.getConversationId(), canSend);

            if (!canSend) {
                // ✅ : Pour les conversations de compétence, ajouter automatiquement l'utilisateur
                if (conversation.getType() == Conversation.ConversationType.SKILL_GROUP) {
                    log.info("🔄 Auto-adding user {} to skill conversation {}", userId, conversation.getId());
                    try {
                        conversationService.addUserToSkillConversationIfNeeded(conversation, userId, token);
                        log.info("✅ User {} successfully added to skill conversation", userId);
                    } catch (Exception e) {
                        log.error("❌ Failed to add user {} to skill conversation: {}", userId, e.getMessage());
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                    }
                } else {
                    log.error("❌ User {} not authorized to send to conversation {}", userId, request.getConversationId());
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                }
            }

            MessageDTO message = messageService.sendMessage(request, token);
            log.info("✅ Message sent successfully: {}", message.getId());
            return ResponseEntity.ok(message);

        } catch (IllegalArgumentException e) {
            log.error("❌ Bad request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("❌ Error sending message: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Récupère les messages d'une conversation
     */
    @GetMapping("/conversation/{conversationId}")
    public ResponseEntity<Page<MessageDTO>> getConversationMessages(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestHeader("Authorization") String token,
            @AuthenticationPrincipal Jwt jwt) {

        try {
            Long userId = userIdResolver.resolveUserId(jwt, token);
            log.info("📥 Fetching messages for conversation {} by user {} (page: {}, size: {})",
                    conversationId, userId, page, size);

            PageRequest pageable = PageRequest.of(page, size);
            Page<MessageDTO> messages = messageService.getConversationMessages(conversationId, userId, pageable);

            log.info("✅ Retrieved {} messages for conversation {} (total: {})",
                    messages.getNumberOfElements(), conversationId, messages.getTotalElements());
            return ResponseEntity.ok(messages);

        } catch (SecurityException e) {
            log.warn("🚫 User access denied to conversation {}", conversationId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            log.error("❌ Error fetching messages for conversation {}: {}", conversationId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Marque les messages comme lus
     */
    @PostMapping("/conversation/{conversationId}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long conversationId,
            @RequestHeader("Authorization") String token,
            @AuthenticationPrincipal Jwt jwt) {

        try {
            Long userId = userIdResolver.resolveUserId(jwt, token);
            log.info("👁️ Marking messages as read for conversation {} by user {}", conversationId, userId);

            messageService.markMessagesAsRead(conversationId, userId);

            log.info("✅ Messages marked as read for conversation {}", conversationId);
            return ResponseEntity.ok().build();

        } catch (SecurityException e) {
            log.warn("🚫 User access denied to conversation {}", conversationId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            log.error("❌ Error marking messages as read for conversation {}: {}", conversationId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Upload de fichier
     */
    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestHeader("Authorization") String token,
            @AuthenticationPrincipal Jwt jwt) {

        try {
            Long userId = userIdResolver.resolveUserId(jwt, token);
            log.info("📎 Uploading file '{}' for user {} (size: {} bytes)",
                    file.getOriginalFilename(), userId, file.getSize());

            String url = messageService.uploadFile(file, userId);

            log.info("✅ File uploaded successfully: {}", url);
            return ResponseEntity.ok(url);

        } catch (IllegalArgumentException e) {
            log.error("❌ Invalid file upload request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("❌ File upload failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * ✅ NOUVEAU: Éditer un message
     */
    @PutMapping("/{messageId}")
    public ResponseEntity<MessageDTO> editMessage(
            @PathVariable Long messageId,
            @RequestBody EditMessageRequest request,
            @RequestHeader("Authorization") String token,
            @AuthenticationPrincipal Jwt jwt) {

        try {
            Long userId = userIdResolver.resolveUserId(jwt, token);
            log.info("✏️ Editing message {} by user {}", messageId, userId);

            MessageDTO message = messageService.editMessage(messageId, request.getContent(), userId);

            log.info("✅ Message {} edited successfully", messageId);
            return ResponseEntity.ok(message);

        } catch (SecurityException e) {
            log.warn("🚫 User {} not authorized to edit message {}", messageId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            log.warn("⚠️ Message {} not found", messageId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ Error editing message {}: {}", messageId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * ✅ NOUVEAU: Supprimer un message
     */
    @DeleteMapping("/{messageId}")
    public ResponseEntity<Void> deleteMessage(
            @PathVariable Long messageId,
            @RequestHeader("Authorization") String token,
            @AuthenticationPrincipal Jwt jwt) {

        try {
            Long userId = userIdResolver.resolveUserId(jwt, token);
            log.info("🗑️ Deleting message {} by user {}", messageId, userId);

            messageService.deleteMessage(messageId, userId);

            log.info("✅ Message {} deleted successfully", messageId);
            return ResponseEntity.ok().build();

        } catch (SecurityException e) {
            log.warn("🚫 User {} not authorized to delete message {}", messageId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            log.warn("⚠️ Message {} not found", messageId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ Error deleting message {}: {}", messageId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * ✅ NOUVEAU: Rechercher des messages dans une conversation
     */
    @GetMapping("/conversation/{conversationId}/search")
    public ResponseEntity<Page<MessageDTO>> searchMessages(
            @PathVariable Long conversationId,
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader("Authorization") String token,
            @AuthenticationPrincipal Jwt jwt) {

        try {
            Long userId = userIdResolver.resolveUserId(jwt, token);
            log.info("🔍 Searching messages in conversation {} for query '{}' by user {}",
                    conversationId, query, userId);

            PageRequest pageable = PageRequest.of(page, size);
            Page<MessageDTO> messages = messageService.searchMessages(conversationId, query, userId, pageable);

            log.info("✅ Found {} messages matching query '{}'", messages.getTotalElements(), query);
            return ResponseEntity.ok(messages);

        } catch (SecurityException e) {
            log.warn("🚫 User access denied to conversation {}", conversationId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            log.error("❌ Error searching messages: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * DTO pour édition de message
     */
    public static class EditMessageRequest {
        private String content;

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}