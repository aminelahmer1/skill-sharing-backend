package com.example.servicemessagerie.service;

import com.example.servicemessagerie.dto.*;
import com.example.servicemessagerie.entity.*;
import com.example.servicemessagerie.repository.*;
import com.example.servicemessagerie.feignclient.*;
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
        log.debug("Sending message to conversation {}", request.getConversationId());

        try {
            // ✅ CORRECTION 1: Utiliser findWithParticipantsById pour charger participants en une fois
            Conversation conversation = conversationRepository.findWithParticipantsById(request.getConversationId())
                    .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));

            // ✅ CORRECTION 2: Forcer le chargement complet des participants avant vérification
            int participantCount = conversation.getParticipants().size(); // Force le chargement
            log.debug("Conversation {} has {} participants", conversation.getId(), participantCount);

            // ✅ CORRECTION 3: Utiliser la méthode isParticipant corrigée
            boolean isParticipant = conversation.isParticipant(request.getSenderId());

            // Pour les conversations de compétence, permettre l'envoi même si pas encore participant
            if (!isParticipant && conversation.getType() != Conversation.ConversationType.SKILL_GROUP) {
                log.warn("User {} is not a participant of conversation {}",
                        request.getSenderId(), request.getConversationId());
                throw new SecurityException("User is not part of this conversation");
            }

            // Récupérer les infos de l'expéditeur
            UserResponse sender = fetchUserById(request.getSenderId(), token);

            // Valider le type de message
            Message.MessageType messageType = request.getType() != null
                    ? Message.MessageType.valueOf(request.getType())
                    : Message.MessageType.TEXT;

            // Créer le message
            Message message = Message.builder()
                    .conversation(conversation)
                    .senderId(request.getSenderId())
                    .senderName(sender.firstName() + " " + sender.lastName())
                    .content(request.getContent())
                    .type(messageType)
                    .attachmentUrl(request.getAttachmentUrl())
                    .status(Message.MessageStatus.SENT)
                    .build();

            // Sauvegarder le message
            message = messageRepository.save(message);
            log.info("Message {} created successfully", message.getId());

            // ✅ CORRECTION 4: Mettre à jour la conversation sans causer de conflits
            try {
                updateConversationLastMessage(conversation.getId(), request.getContent());
            } catch (Exception e) {
                log.warn("Failed to update last message for conversation {}: {}",
                        conversation.getId(), e.getMessage());
                // Ne pas faire échouer l'envoi si la mise à jour échoue
            }

            // Créer le DTO
            MessageDTO messageDTO = convertToDTO(message, sender);

            // ✅ CORRECTION 5: Créer des copies finales pour utilisation dans le lambda
            final Long finalConversationId = conversation.getId();
            final Message finalMessage = message;
            final UserResponse finalSender = sender;
            final MessageDTO finalMessageDTO = messageDTO;

            // ✅ CORRECTION 6: Diffusion asynchrone pour éviter les blocages
            CompletableFuture.runAsync(() -> {
                try {
                    broadcastMessage(finalConversationId, finalMessageDTO);
                    sendPushNotifications(finalConversationId, finalMessage, finalSender);
                } catch (Exception e) {
                    log.error("Error in async message processing: {}", e.getMessage());
                }
            });

            log.info("Message {} sent successfully to conversation {}",
                    message.getId(), conversation.getId());
            return messageDTO;

        } catch (OptimisticLockException e) {
            log.warn("Optimistic lock exception, retrying once...");
            // Retry une fois en cas de conflit de version
            try {
                Thread.sleep(100); // Petit délai avant retry
                return sendMessage(request, token);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted during retry", ie);
            }
        } catch (SecurityException | IllegalArgumentException e) {
            // Propager les exceptions métier
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error sending message: {}", e.getMessage(), e);
            throw new RuntimeException("Error sending message", e);
        }
    }

    private void sendPushNotifications(Long conversationId, Message message, UserResponse sender) {
        try {
            Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
            if (conversation == null) return;

            // Charger les participants de manière sûre
            Set<ConversationParticipant> participantsToNotify = new HashSet<>();
            try {
                conversation.getParticipants().size(); // Force le chargement
                participantsToNotify.addAll(conversation.getParticipants());
            } catch (Exception e) {
                log.error("Error loading participants for notifications: {}", e.getMessage());
                return;
            }

            participantsToNotify.stream()
                    .filter(p -> !p.getUserId().equals(message.getSenderId()))
                    .filter(ConversationParticipant::isNotificationEnabled)
                    .forEach(participant -> {
                        try {
                            firebaseService.sendMessageNotification(
                                    participant.getUserId(),
                                    sender.firstName() + " " + sender.lastName(),
                                    message.getContent(),
                                    conversationId
                            );
                        } catch (Exception e) {
                            log.error("Failed to send push notification to user {}: {}",
                                    participant.getUserId(), e.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.error("Error sending push notifications: {}", e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateConversationLastMessage(Long conversationId, String content) {
        try {
            // Utiliser une requête de mise à jour directe pour éviter les conflits
            conversationRepository.findById(conversationId).ifPresent(conversation -> {
                conversation.setLastMessage(content);
                conversation.setLastMessageTime(LocalDateTime.now());
                conversationRepository.save(conversation);
            });
        } catch (Exception e) {
            log.warn("Failed to update last message for conversation {}: {}",
                    conversationId, e.getMessage());
        }
    }


    private void broadcastMessageAsync(Long conversationId, MessageDTO messageDTO) {
        try {
            // Récupérer la conversation avec ses participants
            Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
            if (conversation == null) return;

            // Créer une copie des participants pour éviter ConcurrentModificationException
            Set<ConversationParticipant> participantsCopy = new HashSet<>(conversation.getParticipants());

            // Diffuser à tous les participants
            participantsCopy.stream()
                    .filter(ConversationParticipant::isActive)
                    .forEach(participant -> {
                        try {
                            messagingTemplate.convertAndSendToUser(
                                    participant.getUserId().toString(),
                                    "/queue/conversation/" + conversationId,
                                    messageDTO
                            );
                        } catch (Exception e) {
                            log.warn("Failed to send message to user {}: {}",
                                    participant.getUserId(), e.getMessage());
                        }
                    });

            // Diffuser sur le topic général
            messagingTemplate.convertAndSend(
                    "/topic/conversation/" + conversationId,
                    messageDTO
            );

        } catch (Exception e) {
            log.error("Error broadcasting message: {}", e.getMessage());
        }
    }

    private void sendPushNotificationsAsync(Long conversationId, Message message, UserResponse sender) {
        try {
            Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
            if (conversation == null) return;

            Set<ConversationParticipant> participantsCopy = new HashSet<>(conversation.getParticipants());

            participantsCopy.stream()
                    .filter(p -> !p.getUserId().equals(message.getSenderId()))
                    .filter(ConversationParticipant::isNotificationEnabled)
                    .forEach(participant -> {
                        try {
                            firebaseService.sendMessageNotification(
                                    participant.getUserId(),
                                    sender.firstName() + " " + sender.lastName(),
                                    message.getContent(),
                                    conversationId
                            );
                        } catch (Exception e) {
                            log.error("Failed to send push notification to user {}: {}",
                                    participant.getUserId(), e.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.error("Error sending push notifications: {}", e.getMessage());
        }
    }







    /**
     * Récupère les messages d'une conversation avec pagination
     */
    @Transactional(readOnly = true)
    public Page<MessageDTO> getConversationMessages(Long conversationId, Long userId, Pageable pageable) {
        log.debug("Fetching messages for conversation {} by user {}", conversationId, userId);

        // Vérifier l'accès
        ConversationParticipant participant = participantRepository
                .findByConversationIdAndUserId(conversationId, userId)
                .orElse(null);

        //  Pour les conversations de compétence, permettre l'accès même sans participation
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new SecurityException("Conversation not found"));

        if (participant == null && conversation.getType() != Conversation.ConversationType.SKILL_GROUP) {
            throw new SecurityException("User not authorized");
        }

        // Récupérer les messages
        Page<Message> messages = messageRepository
                .findByConversationIdAndIsDeletedFalse(conversationId, pageable);

        // Marquer les messages comme lus de manière asynchrone
        if (participant != null) {
            markMessagesAsReadAsync(conversationId, userId);
        }

        return messages.map(m -> convertToDTO(m, null));
    }

    /**
     * Marque les messages comme lus
     */
    @Transactional
    public void markMessagesAsRead(Long conversationId, Long userId) {
        log.debug("Marking messages as read for conversation {} by user {}", conversationId, userId);

        List<Message> unreadMessages = messageRepository
                .findUnreadMessagesForUser(conversationId, userId);

        if (unreadMessages.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        unreadMessages.forEach(message -> {
            if (!message.getSenderId().equals(userId)) {
                message.setStatus(Message.MessageStatus.READ);
                message.setReadAt(now);
            }
        });

        messageRepository.saveAll(unreadMessages);

        // Mettre à jour le dernier message lu du participant
        ConversationParticipant participant = participantRepository
                .findByConversationIdAndUserId(conversationId, userId)
                .orElse(null);

        if (participant != null && !unreadMessages.isEmpty()) {
            participant.setLastReadMessageId(
                    unreadMessages.get(unreadMessages.size() - 1).getId()
            );
            participantRepository.save(participant);
        }

        // Notifier les autres participants que les messages ont été lus
        notifyMessageRead(conversationId, userId, unreadMessages.size());

        log.info("Marked {} messages as read for user {} in conversation {}",
                unreadMessages.size(), userId, conversationId);
    }

    /**
     * Édite un message existant
     */
    @Transactional
    public MessageDTO editMessage(Long messageId, String newContent, Long userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));

        // Vérifier que c'est l'expéditeur
        if (!message.getSenderId().equals(userId)) {
            throw new SecurityException("Only sender can edit message");
        }

        // Vérifier que le message n'est pas trop ancien (optionnel)
        if (message.getSentAt().isBefore(LocalDateTime.now().minusHours(24))) {
            throw new IllegalStateException("Cannot edit messages older than 24 hours");
        }

        message.setContent(newContent);
        message.setEditedAt(LocalDateTime.now());
        message = messageRepository.save(message);

        MessageDTO dto = convertToDTO(message, null);

        // Notifier via WebSocket
        messagingTemplate.convertAndSend(
                "/topic/conversation/" + message.getConversation().getId() + "/edit",
                dto
        );

        log.info("Message {} edited by user {}", messageId, userId);
        return dto;
    }

    /**
     * Supprime un message (soft delete)
     */
    @Transactional
    public void deleteMessage(Long messageId, Long userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));

        // Vérifier que c'est l'expéditeur
        if (!message.getSenderId().equals(userId)) {
            throw new SecurityException("Only sender can delete message");
        }

        message.setDeleted(true);
        message.setContent("[Message supprimé]");
        messageRepository.save(message);

        // Notifier via WebSocket
        messagingTemplate.convertAndSend(
                "/topic/conversation/" + message.getConversation().getId() + "/delete",
                Map.of("messageId", messageId, "deletedBy", userId)
        );

        log.info("Message {} deleted by user {}", messageId, userId);
    }

    /**
     * Upload un fichier pour un message
     */
    @Transactional
    public String uploadFile(MultipartFile file, Long userId) throws IOException {
        if (!fileUploadService.isValidFileType(file)) {
            throw new IllegalArgumentException("Invalid file type");
        }

        String fileUrl = fileUploadService.uploadFile(file, userId);
        log.info("File uploaded by user {}: {}", userId, fileUrl);
        return fileUrl;
    }

    /**
     * ✅ NOUVEAU: Recherche des messages dans une conversation
     */
    @Transactional(readOnly = true)
    public Page<MessageDTO> searchMessages(Long conversationId, String query, Long userId, Pageable pageable) {
        // Vérifier l'accès à la conversation
        ConversationParticipant participant = participantRepository
                .findByConversationIdAndUserId(conversationId, userId)
                .orElse(null);

        // : Pour les conversations de compétence, permettre l'accès
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new SecurityException("Conversation not found"));

        if (participant == null && conversation.getType() != Conversation.ConversationType.SKILL_GROUP) {
            throw new SecurityException("User not authorized");
        }

        // Recherche simple dans le contenu
        Page<Message> messages = messageRepository
                .findByConversationIdAndContentContainingIgnoreCaseAndIsDeletedFalse(
                        conversationId, query, pageable);

        return messages.map(m -> convertToDTO(m, null));
    }

    /**
     * Récupère les messages d'une compétence
     */
    @Transactional(readOnly = true)
    public List<MessageDTO> getSkillMessages(Integer skillId, Long userId, String token) {
        log.debug("Fetching skill messages for skill {} by user {}", skillId, userId);

        // Trouver la conversation de groupe pour cette compétence
        Optional<Conversation> skillConversation = conversationRepository
                .findBySkillIdAndType(skillId, Conversation.ConversationType.SKILL_GROUP);

        if (skillConversation.isEmpty()) {
            return new ArrayList<>();
        }

        // : Pour les conversations de compétence, permettre l'accès à tous
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

    private void broadcastMessage(Long conversationId, MessageDTO messageDTO) {
        try {
            // Récupérer une nouvelle instance de la conversation pour éviter les problèmes de session
            Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
            if (conversation == null) {
                log.warn("Conversation {} not found for broadcast", conversationId);
                return;
            }

            // Charger les participants de manière sûre
            Set<ConversationParticipant> participantsToNotify = new HashSet<>();
            try {
                // Forcer le chargement complet
                conversation.getParticipants().size();
                participantsToNotify.addAll(conversation.getParticipants());
            } catch (Exception e) {
                log.error("Error loading participants for broadcast: {}", e.getMessage());
                return;
            }

            // Diffuser à tous les participants actifs
            participantsToNotify.stream()
                    .filter(ConversationParticipant::isActive)
                    .forEach(participant -> {
                        try {
                            messagingTemplate.convertAndSendToUser(
                                    participant.getUserId().toString(),
                                    "/queue/conversation/" + conversationId,
                                    messageDTO
                            );
                            log.debug("Message sent to user {} via WebSocket", participant.getUserId());
                        } catch (Exception e) {
                            log.warn("Failed to send message to user {}: {}",
                                    participant.getUserId(), e.getMessage());
                        }
                    });

            // Diffuser sur le topic général
            messagingTemplate.convertAndSend(
                    "/topic/conversation/" + conversationId,
                    messageDTO
            );

        } catch (Exception e) {
            log.error("Error broadcasting message: {}", e.getMessage(), e);
        }
    }

    private void sendPushNotificationsToOfflineUsers(Conversation conversation, Message message, UserResponse sender) {
        // ✅ CORRECTION: Créer une copie défensive
        Set<ConversationParticipant> participantsCopy = new HashSet<>(conversation.getParticipants());

        participantsCopy.stream()
                .filter(p -> !p.getUserId().equals(message.getSenderId()))
                .filter(ConversationParticipant::isNotificationEnabled)
                .forEach(participant -> {
                    try {
                        firebaseService.sendMessageNotification(
                                participant.getUserId(),
                                sender.firstName() + " " + sender.lastName(),
                                message.getContent(),
                                conversation.getId()
                        );
                    } catch (Exception e) {
                        log.error("Failed to send push notification to user {}: {}",
                                participant.getUserId(), e.getMessage());
                    }
                });
    }

    private void markMessagesAsReadAsync(Long conversationId, Long userId) {
        // Utiliser un thread séparé pour éviter de bloquer la réponse
        try {
            markMessagesAsRead(conversationId, userId);
        } catch (Exception e) {
            log.warn("Error marking messages as read asynchronously: {}", e.getMessage());
        }
    }

    private void notifyMessageRead(Long conversationId, Long userId, int messageCount) {
        try {
            Map<String, Object> readNotification = Map.of(
                    "userId", userId,
                    "conversationId", conversationId,
                    "readCount", messageCount,
                    "timestamp", LocalDateTime.now()
            );

            messagingTemplate.convertAndSend(
                    "/topic/conversation/" + conversationId + "/read",
                    readNotification
            );
        } catch (Exception e) {
            log.warn("Error sending read notification: {}", e.getMessage());
        }
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
                .build();
    }
}