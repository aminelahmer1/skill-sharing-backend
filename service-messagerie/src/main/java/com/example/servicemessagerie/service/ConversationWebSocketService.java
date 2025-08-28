package com.example.servicemessagerie.service;

import com.example.servicemessagerie.dto.ConversationDTO;
import com.example.servicemessagerie.dto.MessageDTO;
import com.example.servicemessagerie.dto.UserResponse;
import com.example.servicemessagerie.entity.Conversation;
import com.example.servicemessagerie.entity.Message;
import com.example.servicemessagerie.feignclient.ExchangeServiceClient;
import com.example.servicemessagerie.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationWebSocketService {

    private final SimpMessagingTemplate messagingTemplate;
    private final ExchangeServiceClient exchangeServiceClient;
    private ConversationRepository conversationRepository;

    /**
     * Diffuse une nouvelle conversation à tous les participants spécifiés
     */

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

    public void broadcastNewMessage(Message message) {
        try {
            MessageDTO messageDTO = convertMessageToDTO(message);
            Long conversationId = message.getConversation().getId();

            // Récupérer tous les participants
            List<Long> participantIds = conversationRepository
                    .findUserIdsByConversationId(conversationId);

            for (Long participantId : participantIds) {
                if (!participantId.equals(message.getSenderId())) {
                    // Envoyer le message
                    messagingTemplate.convertAndSendToUser(
                            participantId.toString(),
                            "/queue/new-message",
                            messageDTO
                    );

                    // Mise à jour du compteur non lu
                    Map<String, Object> unreadIncrement = new HashMap<>();
                    unreadIncrement.put("conversationId", conversationId);
                    unreadIncrement.put("action", "INCREMENT");
                    unreadIncrement.put("messageId", message.getId());

                    messagingTemplate.convertAndSendToUser(
                            participantId.toString(),
                            "/queue/unread-update",
                            unreadIncrement
                    );
                }
            }

            // Diffuser aussi sur le topic de la conversation
            messagingTemplate.convertAndSend(
                    "/topic/conversation/" + conversationId,
                    messageDTO
            );

            log.debug("✅ New message broadcasted for conversation {}", conversationId);

        } catch (Exception e) {
            log.error("❌ Error broadcasting new message: {}", e.getMessage());
        }
    }










    public void broadcastReadReceipt(Long conversationId, Long readByUserId, int messagesRead) {
        log.info("📖 Broadcasting read receipt for conversation {} by user {}", conversationId, readByUserId);

        try {
            // Créer le payload de notification
            Map<String, Object> readReceipt = new HashMap<>();
            readReceipt.put("conversationId", conversationId);
            readReceipt.put("readByUserId", readByUserId);
            readReceipt.put("messagesRead", messagesRead);
            readReceipt.put("timestamp", LocalDateTime.now().toString());
            readReceipt.put("type", "READ_RECEIPT");

            // 1. Diffuser sur le topic de la conversation
            messagingTemplate.convertAndSend(
                    "/topic/conversation/" + conversationId + "/read",
                    readReceipt
            );

            // 2. Récupérer tous les participants
            List<Long> participantIds = conversationRepository.findUserIdsByConversationId(conversationId);

            // 3. Envoyer à chaque participant individuellement (sauf celui qui a lu)
            for (Long participantId : participantIds) {
                if (!participantId.equals(readByUserId)) {
                    // Queue personnelle pour les receipts
                    messagingTemplate.convertAndSendToUser(
                            participantId.toString(),
                            "/queue/read-receipt",
                            readReceipt
                    );

                    // Mise à jour du compteur non lu
                    Map<String, Object> unreadUpdate = new HashMap<>();
                    unreadUpdate.put("conversationId", conversationId);
                    unreadUpdate.put("action", "DECREMENT");
                    unreadUpdate.put("count", messagesRead);

                    messagingTemplate.convertAndSendToUser(
                            participantId.toString(),
                            "/queue/unread-update",
                            unreadUpdate
                    );
                }
            }

            log.info("✅ Read receipt broadcasted to {} participants", participantIds.size() - 1);

        } catch (Exception e) {
            log.error("❌ Error broadcasting read receipt: {}", e.getMessage(), e);
        }
    }







    public void syncReadStateAcrossDevices(Long userId, Long conversationId, Long lastReadMessageId) {
        try {
            Map<String, Object> syncData = new HashMap<>();
            syncData.put("conversationId", conversationId);
            syncData.put("lastReadMessageId", lastReadMessageId);
            syncData.put("timestamp", LocalDateTime.now().toString());
            syncData.put("type", "READ_SYNC");

            // Envoyer à toutes les sessions de cet utilisateur
            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/sync-read-state",
                    syncData
            );

            log.debug("✅ Read state synced for user {} on conversation {}", userId, conversationId);

        } catch (Exception e) {
            log.error("❌ Error syncing read state: {}", e.getMessage());
        }
    }

    /**
     * Diffuse une nouvelle conversation
     */
    public void broadcastNewConversation(ConversationDTO conversation, Set<Long> participantIds) {
        log.info("Broadcasting new conversation {} to {} participants",
                conversation.getId(), participantIds.size());

        for (Long participantId : participantIds) {
            try {
                messagingTemplate.convertAndSendToUser(
                        participantId.toString(),
                        "/queue/new-conversation",
                        conversation
                );
            } catch (Exception e) {
                log.error("Failed to send to user {}: {}", participantId, e.getMessage());
            }
        }
    }

    private MessageDTO convertMessageToDTO(Message message) {
        return MessageDTO.builder()
                .id(message.getId())
                .conversationId(message.getConversation().getId())
                .senderId(message.getSenderId())
                .senderName(message.getSenderName())
                .content(message.getContent())
                .type(message.getType().name())
                .status(message.getStatus().name())
                .attachmentUrl(message.getAttachmentUrl())
                .sentAt(message.getSentAt())
                .readAt(message.getReadAt())
                .editedAt(message.getEditedAt())
                .isDeleted(message.isDeleted())
                .build();
    }
}





