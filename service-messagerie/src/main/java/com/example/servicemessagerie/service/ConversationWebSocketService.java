package com.example.servicemessagerie.service;

import com.example.servicemessagerie.dto.ConversationDTO;
import com.example.servicemessagerie.dto.UserResponse;
import com.example.servicemessagerie.entity.Conversation;
import com.example.servicemessagerie.feignclient.ExchangeServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationWebSocketService {

    private final SimpMessagingTemplate messagingTemplate;
    private final ExchangeServiceClient exchangeServiceClient;

    /**
     * Diffuse une nouvelle conversation à tous les participants spécifiés
     */
    public void broadcastNewConversation(ConversationDTO conversation, Set<Long> participantIds) {
        log.info("Broadcasting new conversation {} to {} participants",
                conversation.getId(), participantIds.size());

        try {
            // Diffuser à chaque participant individuellement
            for (Long participantId : participantIds) {
                try {
                    // Sur la queue personnelle de chaque utilisateur
                    messagingTemplate.convertAndSendToUser(
                            participantId.toString(),
                            "/queue/new-conversation",
                            conversation
                    );

                    // Aussi sur /queue/conversations pour mise à jour de la liste
                    messagingTemplate.convertAndSendToUser(
                            participantId.toString(),
                            "/queue/conversations",
                            conversation
                    );

                    log.debug("Sent to user {}", participantId);

                } catch (Exception e) {
                    log.error("Failed to send to user {}: {}", participantId, e.getMessage());
                }
            }

            log.info("Broadcasted conversation {} to {} participants",
                    conversation.getId(), participantIds.size());

        } catch (Exception e) {
            log.error("Error broadcasting conversation: {}", e.getMessage(), e);
        }
    }

    /**
     * Diffuse une nouvelle conversation à tous les participants
     */
    public void broadcastNewSkillConversation(ConversationDTO conversation, Integer skillId, String token) {
        log.info("📡 Broadcasting new skill conversation {} for skill {}", conversation.getId(), skillId);

        try {
            // ✅ Récupérer TOUS les utilisateurs de la compétence (producteur + receivers)
            List<UserResponse> allSkillUsers = exchangeServiceClient.getSkillUsersSimple(skillId, token);

            Set<Long> participantIds = allSkillUsers.stream()
                    .map(UserResponse::id)
                    .collect(Collectors.toSet());

            // ✅ Diffuser à chaque utilisateur (producteur + receivers)
            for (Long userId : participantIds) {
                try {
                    messagingTemplate.convertAndSendToUser(
                            userId.toString(),
                            "/queue/new-conversation",
                            conversation
                    );
                    log.debug("✅ Sent to user {}", userId);
                } catch (Exception e) {
                    log.error("❌ Failed to send to user {}: {}", userId, e.getMessage());
                }
            }

            // ✅ Topic global pour cette compétence
            messagingTemplate.convertAndSend(
                    "/topic/skill/" + skillId + "/new-conversation",
                    conversation
            );

            log.info("✅ Broadcasted conversation {} to {} users for skill {}",
                    conversation.getId(), participantIds.size(), skillId);

        } catch (Exception e) {
            log.error("❌ Error broadcasting skill conversation: {}", e.getMessage(), e);
        }
    }
}