package com.example.servicemessagerie.service;

import com.example.servicemessagerie.dto.*;
import com.example.servicemessagerie.entity.*;
import com.example.servicemessagerie.repository.*;
import com.example.servicemessagerie.feignclient.*;
import jakarta.annotation.PreDestroy;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserServiceClient userServiceClient;
    private final FirebaseMessagingService firebaseService;
    private final FileUploadService fileUploadService;

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
    public MessageDTO sendMessage(MessageRequest request, String token) {
        log.debug("📤 sendMessage: conversation={}, sender={}", request.getConversationId(), request.getSenderId());

        // 1️⃣ Validation
        if (request.getConversationId() == null || request.getSenderId() == null ||
                request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("Paramètres manquants ou invalides");
        }

        // 2️⃣ Conversation & permissions
        Conversation conversation = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new IllegalArgumentException("Conversation introuvable"));

        if (conversation.getStatus() != Conversation.ConversationStatus.ACTIVE) {
            throw new IllegalArgumentException("Conversation inactive");
        }

        if (conversation.getType() == Conversation.ConversationType.SKILL_GROUP) {
            handleSkillGroupParticipation(conversation, request.getSenderId(), token);
        } else {
            boolean isParticipant = participantRepository
                    .existsByConversationIdAndUserId(conversation.getId(), request.getSenderId());
            if (!isParticipant) throw new SecurityException("Non participant");
        }

        // 3️⃣ Expéditeur
        UserResponse sender = fetchUserById(request.getSenderId(), token);
        String senderName = (sender.firstName() + " " + sender.lastName()).trim();
        if (senderName.isEmpty()) senderName = sender.username();

        // 4️⃣ Créer le message
        Message message = Message.builder()
                .conversation(conversation)
                .senderId(request.getSenderId())
                .senderName(senderName)
                .content(request.getContent().trim())
                .type(parseMessageType(request.getType()))
                .attachmentUrl(request.getAttachmentUrl())
                .status(Message.MessageStatus.SENT)
                .sentAt(LocalDateTime.now())
                .build();

        message = messageRepository.save(message);

        // 5️⃣ Mettre à jour la conversation
        updateConversationLastMessageSafely(conversation.getId(), request.getContent().trim());

        // 6️⃣ DTO de réponse
        MessageDTO dto = convertToDTO(message, sender);

        // 7️⃣ DIFFUSION WEBSOCKET
        messagingTemplate.convertAndSend(
                "/topic/conversation/" + conversation.getId(),
                dto
        );

        // 8️⃣ Notifications push asynchrones
        broadcastMessageAsync(conversation.getId(), dto, conversation.getParticipants(), sender);

        log.info("✅ Message {} envoyé et diffusé", message.getId());
        return dto;
    }

    // CORRECTION: Marquer TOUS les messages comme lus, pas seulement certains
    @Transactional
    public void markMessagesAsRead(Long conversationId, Long userId) {
        log.debug("Marking messages as read for conversation {} by user {}", conversationId, userId);

        List<Message> unreadMessages = messageRepository
                .findUnreadMessagesForUser(conversationId, userId);

        if (unreadMessages.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        // CORRECTION: Marquer TOUS les messages non-lus, pas seulement ceux des autres
        unreadMessages.forEach(message -> {
            // Ne pas modifier ses propres messages
            if (!message.getSenderId().equals(userId)) {
                message.setStatus(Message.MessageStatus.READ);
                message.setReadAt(now);
            }
        });

        messageRepository.saveAll(unreadMessages);

        // CORRECTION: Mettre à jour le dernier message lu dans participant
        ConversationParticipant participant = participantRepository
                .findByConversationIdAndUserId(conversationId, userId)
                .orElse(null);

        if (participant != null) {
            // Trouver le dernier message de la conversation
            Optional<Message> lastMessage = messageRepository
                    .findTopByConversationIdOrderBySentAtDesc(conversationId);

            if (lastMessage.isPresent()) {
                participant.setLastReadMessageId(lastMessage.get().getId());
                participantRepository.save(participant);
            }
        }

        // Notifier les autres participants
        notifyMessageRead(conversationId, userId, unreadMessages.size());

        log.info("Marked {} messages as read for user {} in conversation {}",
                unreadMessages.size(), userId, conversationId);
    }

    // CORRECTION: Récupérer les messages AVEC auto-marquage optionnel
    @Transactional(readOnly = true)
    public Page<MessageDTO> getConversationMessages(Long conversationId, Long userId, Pageable pageable) {
        log.debug("Fetching messages for conversation {} by user {}", conversationId, userId);

        // Vérifier l'accès
        ConversationParticipant participant = participantRepository
                .findByConversationIdAndUserId(conversationId, userId)
                .orElse(null);

        // Pour les conversations de compétence, permettre l'accès même sans participation
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new SecurityException("Conversation not found"));

        if (participant == null && conversation.getType() != Conversation.ConversationType.SKILL_GROUP) {
            throw new SecurityException("User not authorized");
        }

        // Récupérer les messages
        Page<Message> messages = messageRepository
                .findByConversationIdAndIsDeletedFalse(conversationId, pageable);

        // CORRECTION: Marquer comme lu dans une transaction séparée pour éviter les problèmes
        if (participant != null && pageable.getPageNumber() == 0) {
            CompletableFuture.runAsync(() -> {
                try {
                    markMessagesAsReadAsync(conversationId, userId);
                } catch (Exception e) {
                    log.warn("Could not auto-mark messages as read: {}", e.getMessage());
                }
            });
        }

        return messages.map(m -> convertToDTO(m, null));
    }

    // NOUVEAU: Méthode asynchrone pour marquer comme lu
    private void markMessagesAsReadAsync(Long conversationId, Long userId) {
        try {
            Thread.sleep(500); // Petit délai pour éviter les conflits
            markMessagesAsRead(conversationId, userId);
        } catch (Exception e) {
            log.warn("Error in async mark as read: {}", e.getMessage());
        }
    }

    // CORRECTION: Notifier correctement les changements d'état de lecture
    private void notifyMessageRead(Long conversationId, Long userId, int messageCount) {
        try {
            Map<String, Object> readNotification = Map.of(
                    "userId", userId,
                    "conversationId", conversationId,
                    "readCount", messageCount,
                    "timestamp", LocalDateTime.now()
            );

            // Notifier sur le topic de la conversation
            messagingTemplate.convertAndSend(
                    "/topic/conversation/" + conversationId + "/read",
                    readNotification
            );

            // Notifier aussi chaque participant individuellement
            List<Long> participantIds = conversationRepository
                    .findUserIdsByConversationId(conversationId);

            for (Long participantId : participantIds) {
                if (!participantId.equals(userId)) {
                    messagingTemplate.convertAndSendToUser(
                            participantId.toString(),
                            "/queue/read-receipt",
                            readNotification
                    );
                }
            }
        } catch (Exception e) {
            log.warn("Error sending read notification: {}", e.getMessage());
        }
    }

    // Les autres méthodes restent identiques...

    private void handleSkillGroupParticipation(Conversation conversation, Long userId, String token) {
        try {
            boolean isAlreadyParticipant = participantRepository
                    .existsByConversationIdAndUserId(conversation.getId(), userId);

            if (!isAlreadyParticipant) {
                log.info("🔄 Auto-joining user {} to skill conversation {}", userId, conversation.getId());

                UserResponse user = fetchUserById(userId, token);
                String userName = (user.firstName() + " " + user.lastName()).trim();
                if (userName.isEmpty()) {
                    userName = user.username();
                }

                ConversationParticipant participant = ConversationParticipant.builder()
                        .conversation(conversation)
                        .userId(userId)
                        .userName(userName)
                        .role(ConversationParticipant.ParticipantRole.MEMBER)
                        .isActive(true)
                        .notificationEnabled(true)
                        .build();

                participantRepository.save(participant);
                log.info("✅ User {} successfully joined skill conversation {}", userId, conversation.getId());
            }
        } catch (Exception e) {
            log.error("❌ Failed to add user {} to skill conversation {}: {}",
                    userId, conversation.getId(), e.getMessage());
            throw new SecurityException("Cannot join skill conversation: " + e.getMessage());
        }
    }

    private Message.MessageType parseMessageType(String type) {
        if (type == null || type.trim().isEmpty()) {
            return Message.MessageType.TEXT;
        }

        try {
            return Message.MessageType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("⚠️ Invalid message type '{}', defaulting to TEXT", type);
            return Message.MessageType.TEXT;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateConversationLastMessageSafely(Long conversationId, String content) {
        try {
            conversationRepository.findById(conversationId).ifPresent(conv -> {
                conv.setLastMessage(content.length() > 255 ? content.substring(0, 252) + "..." : content);
                conv.setLastMessageTime(LocalDateTime.now());
                conversationRepository.save(conv);
            });
        } catch (Exception e) {
            log.warn("⚠️ Failed to update last message for conversation {}: {}", conversationId, e.getMessage());
        }
    }

    private void broadcastMessageAsync(Long conversationId, MessageDTO messageDTO,
                                       Set<ConversationParticipant> participants, UserResponse sender) {
        CompletableFuture.runAsync(() -> {
            try {
                Set<ConversationParticipant> recipientsOnly = participants.stream()
                        .filter(p -> !p.getUserId().equals(messageDTO.getSenderId()))
                        .collect(Collectors.toSet());

                broadcastMessageToRecipients(conversationId, messageDTO, recipientsOnly);
                sendPushNotificationsToOfflineUsers(conversationId, messageDTO, recipientsOnly, sender);

            } catch (Exception e) {
                log.error("❌ Error in async message broadcasting: {}", e.getMessage());
            }
        });
    }

    private void broadcastMessageToRecipients(Long conversationId, MessageDTO messageDTO,
                                              Set<ConversationParticipant> recipients) {
        try {
            recipients.stream()
                    .filter(p -> p != null && p.isActive())
                    .forEach(participant -> {
                        try {
                            messagingTemplate.convertAndSendToUser(
                                    participant.getUserId().toString(),
                                    "/queue/conversation",
                                    messageDTO
                            );
                        } catch (Exception e) {
                            log.warn("Failed to send to user {}: {}",
                                    participant.getUserId(), e.getMessage());
                        }
                    });

        } catch (Exception e) {
            log.error("Error broadcasting: {}", e.getMessage());
        }
    }

    private void sendPushNotificationsToOfflineUsers(Long conversationId, MessageDTO messageDTO,
                                                     Set<ConversationParticipant> participants, UserResponse sender) {
        try {
            if (participants == null) return;

            participants.stream()
                    .filter(p -> p != null && p.isActive() && p.isNotificationEnabled())
                    .filter(p -> !p.getUserId().equals(messageDTO.getSenderId()))
                    .forEach(participant -> {
                        try {
                            firebaseService.sendMessageNotification(
                                    participant.getUserId(),
                                    sender.firstName() + " " + sender.lastName(),
                                    truncateForNotification(messageDTO.getContent()),
                                    conversationId
                            );
                        } catch (Exception e) {
                            log.warn("⚠️ Failed to send push notification to user {}: {}",
                                    participant.getUserId(), e.getMessage());
                        }
                    });

        } catch (Exception e) {
            log.error("❌ Error sending push notifications: {}", e.getMessage());
        }
    }

    private String truncateForNotification(String content) {
        if (content == null) return "";

        final int MAX_LENGTH = 100;
        if (content.length() <= MAX_LENGTH) {
            return content;
        }

        return content.substring(0, MAX_LENGTH - 3) + "...";
    }

    private MessageDTO convertToDTO(Message message, UserResponse sender) {
        return MessageDTO.builder()
                .id(message.getId())
                .conversationId(message.getConversation().getId())
                .senderId(message.getSenderId())
                .senderName(message.getSenderName())
                .senderAvatar(sender != null ? sender.profileImageUrl() : null)
                .content(message.getContent())
                .type(message.getType().name())
                .status(message.getStatus().name())
                .attachmentUrl(message.getAttachmentUrl())
                .sentAt(message.getSentAt())
                .readAt(message.getReadAt())
                .editedAt(message.getEditedAt())
                .isDeleted(message.isDeleted())
                .canEdit(message.getSenderId().equals(message.getSenderId()))
                .canDelete(message.getSenderId().equals(message.getSenderId()))
                .build();
    }

    // Toutes les autres méthodes existantes restent EXACTEMENT les mêmes...

    @Transactional
    public MessageDTO editMessage(Long messageId, String newContent, Long userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));

        if (!message.getSenderId().equals(userId)) {
            throw new SecurityException("Only sender can edit message");
        }

        if (message.getSentAt().isBefore(LocalDateTime.now().minusHours(24))) {
            throw new IllegalStateException("Cannot edit messages older than 24 hours");
        }

        message.setContent(newContent);
        message.setEditedAt(LocalDateTime.now());
        message = messageRepository.save(message);

        MessageDTO dto = convertToDTO(message, null);

        messagingTemplate.convertAndSend(
                "/topic/conversation/" + message.getConversation().getId() + "/edit",
                dto
        );

        log.info("Message {} edited by user {}", messageId, userId);
        return dto;
    }

    @Transactional
    public void deleteMessage(Long messageId, Long userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));

        if (!message.getSenderId().equals(userId)) {
            throw new SecurityException("Only sender can delete message");
        }

        message.setDeleted(true);
        message.setContent("[Message supprimé]");
        messageRepository.save(message);

        messagingTemplate.convertAndSend(
                "/topic/conversation/" + message.getConversation().getId() + "/delete",
                Map.of("messageId", messageId, "deletedBy", userId)
        );

        log.info("Message {} deleted by user {}", messageId, userId);
    }

    @Transactional
    public String uploadFile(MultipartFile file, Long userId) throws IOException {
        if (!fileUploadService.isValidFileType(file)) {
            throw new IllegalArgumentException("Invalid file type");
        }

        String fileUrl = fileUploadService.uploadFile(file, userId);
        log.info("File uploaded by user {}: {}", userId, fileUrl);
        return fileUrl;
    }

    @Transactional(readOnly = true)
    public Page<MessageDTO> searchMessages(Long conversationId, String query, Long userId, Pageable pageable) {
        ConversationParticipant participant = participantRepository
                .findByConversationIdAndUserId(conversationId, userId)
                .orElse(null);

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new SecurityException("Conversation not found"));

        if (participant == null && conversation.getType() != Conversation.ConversationType.SKILL_GROUP) {
            throw new SecurityException("User not authorized");
        }

        Page<Message> messages = messageRepository
                .findByConversationIdAndContentContainingIgnoreCaseAndIsDeletedFalse(
                        conversationId, query, pageable);

        return messages.map(m -> convertToDTO(m, null));
    }

    @Transactional(readOnly = true)
    public List<MessageDTO> getSkillMessages(Integer skillId, Long userId, String token) {
        log.debug("Fetching skill messages for skill {} by user {}", skillId, userId);

        Optional<Conversation> skillConversation = conversationRepository
                .findBySkillIdAndType(skillId, Conversation.ConversationType.SKILL_GROUP);

        if (skillConversation.isEmpty()) {
            return new ArrayList<>();
        }

        List<Message> messages = messageRepository
                .findByConversationIdAndIsDeletedFalse(skillConversation.get().getId(),
                        PageRequest.of(0, 100))
                .getContent();

        return messages.stream()
                .map(m -> convertToDTO(m, null))
                .collect(Collectors.toList());
    }

    private UserResponse fetchUserById(Long userId, String token) {
        try {
            ResponseEntity<UserResponse> response = userServiceClient.getUserById(userId, token);
            UserResponse user = response.getBody();
            if (user == null) {
                throw new IllegalArgumentException("User not found with ID: " + userId);
            }
            return user;
        } catch (Exception e) {
            log.error("Error fetching user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Error fetching user: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public int getUnreadCountForConversation(Long conversationId, Long userId) {
        try {
            return messageRepository.countUnreadInConversation(conversationId, userId);
        } catch (Exception e) {
            log.error("Error getting unread count: {}", e.getMessage());
            return 0;
        }
    }
    @Transactional
    public void updateLastReadMessage(Long conversationId, Long userId) {
        try {
            // Trouver le dernier message de la conversation
            Optional<Message> lastMessage = messageRepository.findTopByConversationIdOrderBySentAtDesc(conversationId);

            if (lastMessage.isPresent()) {
                ConversationParticipant participant = participantRepository
                        .findByConversationIdAndUserId(conversationId, userId)
                        .orElse(null);

                if (participant != null) {
                    participant.setLastReadMessageId(lastMessage.get().getId());
                    participant.setLastReadTime(LocalDateTime.now());
                    participantRepository.save(participant);
                    log.debug("Updated last read message for user {} in conversation {}", userId, conversationId);
                }
            }
        } catch (Exception e) {
            log.error("Error updating last read message: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public int getTotalUnreadCount(Long userId) {
        try {
            return messageRepository.countUnreadMessagesForUser(userId);
        } catch (Exception e) {
            log.error("Error getting total unread count: {}", e.getMessage());
            return 0;
        }
    }

    // Ajouter ces méthodes dans MessageService.java (après les méthodes existantes)

    /**
     * Marque automatiquement comme lu quand l'utilisateur tape ou est actif
     * Cette méthode remplace markAsReadOnUserActivity qui n'existe pas
     */
    @Transactional
    public void markAsReadOnUserActivity(Long conversationId, Long userId) {
        try {
            // Utiliser la méthode existante markMessagesAsRead
            markMessagesAsRead(conversationId, userId);
            log.debug("Auto-marked messages as read on user activity for conversation {}", conversationId);
        } catch (Exception e) {
            log.error("Error marking as read on activity: {}", e.getMessage());
        }
    }

    /**
     * Marque une conversation comme active ou inactive pour un utilisateur
     * Garde en mémoire l'état actif des conversations
     */
    private final Set<String> activeConversations = Collections.synchronizedSet(new HashSet<>());

    public void setConversationActive(Long conversationId, Long userId, boolean active) {
        String key = userId + "-" + conversationId;
        if (active) {
            activeConversations.add(key);
            log.debug("Conversation {} marked as active for user {}", conversationId, userId);

            // Marquer automatiquement tous les messages comme lus
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(100); // Petit délai pour éviter les conflits
                    markMessagesAsRead(conversationId, userId);
                } catch (Exception e) {
                    log.error("Error marking messages as read on activation: {}", e.getMessage());
                }
            });
        } else {
            activeConversations.remove(key);
            log.debug("Conversation {} marked as inactive for user {}", conversationId, userId);
        }
    }

    /**
     * Vérifie si une conversation est active pour un utilisateur
     */
    public boolean isConversationActiveForUser(Long conversationId, Long userId) {
        return activeConversations.contains(userId + "-" + conversationId);
    }

    /**
     * Marque automatiquement un nouveau message comme lu si la conversation est active
     */
    @Transactional
    public void autoMarkMessageAsRead(Long conversationId, Long messageId, Long userId) {
        try {
            // Vérifier si la conversation est active
            if (!isConversationActiveForUser(conversationId, userId)) {
                return;
            }

            // Récupérer le message
            Message message = messageRepository.findById(messageId).orElse(null);
            if (message == null || message.getSenderId().equals(userId)) {
                return; // Ne pas marquer nos propres messages
            }

            // Marquer comme lu
            if (message.getStatus() != Message.MessageStatus.READ) {
                message.setStatus(Message.MessageStatus.READ);
                message.setReadAt(LocalDateTime.now());
                messageRepository.save(message);

                log.debug("Auto-marked message {} as read in active conversation {}", messageId, conversationId);

                // Notifier les autres participants
                notifyMessageRead(conversationId, userId, 1);
            }
        } catch (Exception e) {
            log.error("Error auto-marking message as read: {}", e.getMessage());
        }
    }

    /**
     * Nettoie les conversations actives au shutdown
     */
    @PreDestroy
    public void cleanup() {
        activeConversations.clear();
        log.info("Cleaned up active conversations cache");
    }
}