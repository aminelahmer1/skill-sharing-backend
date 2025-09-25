package com.example.servicemessagerie.controller;

import com.example.servicemessagerie.dto.MessageDTO;
import com.example.servicemessagerie.dto.TypingIndicatorDTO;
import com.example.servicemessagerie.dto.UserResponse;
import com.example.servicemessagerie.feignclient.UserServiceClient;
import com.example.servicemessagerie.service.MessageService;
import com.example.servicemessagerie.util.UserIdResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Controller;
import feign.FeignException;

import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class MessageWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageService messageService;
    private final UserServiceClient userServiceClient; // ✅ AJOUTÉ
    private final UserIdResolver userIdResolver; // ✅ AJOUTÉ

    @MessageMapping("/conversation/{conversationId}/send")
    public void sendMessage(
            @DestinationVariable Long conversationId,
            @Payload MessageDTO message,
            SimpMessageHeaderAccessor headerAccessor) {

        try {
            JwtAuthenticationToken auth = (JwtAuthenticationToken) headerAccessor.getUser();
            if (auth == null) {
                throw new SecurityException("Authentication required");
            }

            Jwt jwt = auth.getToken();

            // ✅ CORRECTION: Utiliser la même logique de résolution que WebSocketConfig
            Long userId = resolveUserId(jwt);

            // Diffuser le message à tous les participants
            messagingTemplate.convertAndSend(
                    "/topic/conversation/" + conversationId,
                    message
            );

            // Pour tous les participants actifs, marquer comme lu automatiquement
            messagingTemplate.convertAndSend(
                    "/topic/conversation/" + conversationId + "/auto-read",
                    Map.of("messageId", message.getId(), "senderId", message.getSenderId())
            );

            log.debug("Message sent to conversation {} by user {}", conversationId, userId);

        } catch (Exception e) {
            log.error("❌ Error handling WebSocket message send: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send message via WebSocket", e);
        }
    }

    @MessageMapping("/conversation/{conversationId}/typing")
    @SendTo("/topic/conversation/{conversationId}/typing")
    public TypingIndicatorDTO handleTyping(
            @DestinationVariable Long conversationId,
            @Payload TypingIndicatorDTO indicator,
            SimpMessageHeaderAccessor headerAccessor) {

        try {
            JwtAuthenticationToken auth = (JwtAuthenticationToken) headerAccessor.getUser();
            if (auth == null) {
                throw new SecurityException("Authentication required");
            }

            Jwt jwt = auth.getToken();

            // ✅ CORRECTION: Utiliser la même logique de résolution que WebSocketConfig
            Long userId = resolveUserId(jwt);

            // Si l'utilisateur tape, marquer les messages comme lus
            if (indicator.isTyping()) {
                try {
                    messageService.markMessagesAsRead(conversationId, userId);
                    log.debug("Auto-marked messages as read for typing user {} in conversation {}",
                            userId, conversationId);
                } catch (Exception e) {
                    log.error("Error auto-marking as read on typing: {}", e.getMessage());
                }
            }

            log.debug("Typing indicator for conversation {} from user {}",
                    conversationId, userId);

            // ✅ CORRECTION: Mettre à jour l'indicateur avec le bon userId
            indicator.setUserId(userId);
            return indicator;

        } catch (Exception e) {
            log.error("❌ Error handling typing indicator: {}", e.getMessage(), e);
            // Retourner un indicateur vide plutôt que de lancer une exception
            return TypingIndicatorDTO.builder()
                    .userId(-1L)
                    .conversationId(conversationId)
                    .isTyping(false)
                    .build();
        }
    }

    @MessageMapping("/conversation/{conversationId}/active")
    public void setConversationActive(
            @DestinationVariable Long conversationId,
            @Payload Map<String, Object> payload,
            SimpMessageHeaderAccessor headerAccessor) {

        try {
            JwtAuthenticationToken auth = (JwtAuthenticationToken) headerAccessor.getUser();
            if (auth == null) {
                throw new SecurityException("Authentication required");
            }

            Jwt jwt = auth.getToken();

            // ✅ CORRECTION: Utiliser la même logique de résolution que WebSocketConfig
            Long userId = resolveUserId(jwt);
            boolean isActive = (boolean) payload.getOrDefault("active", false);

            // Notifier que la conversation est active/inactive
            log.debug("Conversation {} marked as {} for user {}",
                    conversationId, isActive ? "active" : "inactive", userId);

            // Si active, marquer tous les messages comme lus
            if (isActive) {
                try {
                    messageService.markMessagesAsRead(conversationId, userId);
                } catch (Exception e) {
                    log.error("Error marking messages as read on activation: {}", e.getMessage());
                }
            }

            // ✅ NOUVEAU: Mettre à jour l'état de la conversation dans MessageService
            messageService.setConversationActive(conversationId, userId, isActive);

        } catch (Exception e) {
            log.error("❌ Error handling conversation active state: {}", e.getMessage(), e);
        }
    }

    @MessageMapping("/presence")
    public void updatePresence(
            @Payload Map<String, Object> presence,
            SimpMessageHeaderAccessor headerAccessor) {

        try {
            JwtAuthenticationToken auth = (JwtAuthenticationToken) headerAccessor.getUser();
            if (auth == null) {
                throw new SecurityException("Authentication required");
            }

            Jwt jwt = auth.getToken();

            // ✅ CORRECTION: Utiliser la même logique de résolution que WebSocketConfig
            Long userId = resolveUserId(jwt);

            // Diffuser la mise à jour de présence aux contacts
            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/presence",
                    presence
            );

            log.debug("Presence updated for user {}: {}", userId, presence);

        } catch (Exception e) {
            log.error("❌ Error handling presence update: {}", e.getMessage(), e);
        }
    }

    /**
     * ✅ NOUVELLE MÉTHODE: Résolution d'ID utilisateur identique à WebSocketConfig
     */
    private Long resolveUserId(Jwt jwt) {
        try {
            String subject = jwt.getSubject();

            // Essayer d'abord de parser comme Long
            try {
                return Long.parseLong(subject);
            } catch (NumberFormatException ex) {
                log.debug("Subject is UUID ({}), fetching user by Keycloak ID", subject);

                // Si c'est un UUID, appeler le service utilisateur
                ResponseEntity<UserResponse> userResponseEntity = userServiceClient.getUserByKeycloakId(
                        subject, "Bearer " + extractTokenFromJwt(jwt)
                );

                UserResponse user = userResponseEntity.getBody();

                if (user != null && user.id() != null) {
                    log.debug("Resolved user ID: {} for Keycloak ID: {}", user.id(), subject);
                    return user.id();
                } else {
                    log.error("User response has null ID for Keycloak ID: {}", subject);
                    throw new SecurityException("User ID is null");
                }
            }
        } catch (FeignException.NotFound notFoundEx) {
            log.error("User not found for Keycloak ID: {}", jwt.getSubject());
            throw new SecurityException("User not found");
        } catch (FeignException feignEx) {
            log.error("Feign error calling user service for Keycloak ID {}: {}",
                    jwt.getSubject(), feignEx.getMessage());
            throw new SecurityException("User service unavailable");
        } catch (Exception generalEx) {
            log.error("Failed to resolve user ID for Keycloak ID {}: {}",
                    jwt.getSubject(), generalEx.getMessage(), generalEx);
            throw new SecurityException("Cannot resolve user ID");
        }
    }

    /**
     * ✅ NOUVELLE MÉTHODE: Extraction du token JWT
     */
    private String extractTokenFromJwt(Jwt jwt) {
        // Le token est déjà décodé, nous devons le reconstruire ou l'avoir en cache
        // Pour simplifier, nous pouvons utiliser une approche alternative
        return jwt.getTokenValue();
    }
}