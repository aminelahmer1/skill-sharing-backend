package com.example.servicemessagerie.service;

import com.example.servicemessagerie.dto.*;
import com.example.servicemessagerie.entity.*;
import com.example.servicemessagerie.repository.*;
import com.example.servicemessagerie.feignclient.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
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


    @Transactional
    public MessageDTO sendMessage(MessageRequest request, String token) {
        log.debug("Sending message to conversation {}", request.getConversationId());

        // Récupérer la conversation
        Conversation conversation = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));

        //  Vérification améliorée des permissions
        boolean isParticipant = conversation.getParticipants().stream()
                .anyMatch(p -> p.getUserId().equals(request.getSenderId()) && p.isActive());

        // Pour les conversations de compétence, permettre l'envoi même si pas encore participant
        if (!isParticipant && conversation.getType() != Conversation.ConversationType.SKILL_GROUP) {
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

        message = messageRepository.save(message);

        // Mettre à jour la conversation
        conversation.updateLastMessage(request.getContent());
        conversationRepository.save(conversation);

        // Créer le DTO
        MessageDTO messageDTO = convertToDTO(message, sender);

        // Diffuser le message via WebSocket
        broadcastMessage(conversation, messageDTO);

        // Envoyer les notifications Push aux utilisateurs hors ligne
        sendPushNotificationsToOfflineUsers(conversation, message, sender);

        // Marquer comme délivré
        message.setStatus(Message.MessageStatus.DELIVERED);
        messageRepository.save(message);

        log.info("Message {} sent successfully to conversation {}", message.getId(), conversation.getId());
        return messageDTO;
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

    private void broadcastMessage(Conversation conversation, MessageDTO messageDTO) {
        try {
            // Diffuser à tous les participants via leur file personnelle
            conversation.getParticipants().stream()
                    .filter(ConversationParticipant::isActive)
                    .forEach(participant -> {
                        try {
                            // Envoyer à l'utilisateur spécifique
                            messagingTemplate.convertAndSendToUser(
                                    participant.getUserId().toString(),
                                    "/queue/conversation/" + conversation.getId(),
                                    messageDTO
                            );
                        } catch (Exception e) {
                            log.warn("Failed to send message to user {}: {}",
                                    participant.getUserId(), e.getMessage());
                        }
                    });

            // Diffuser aussi sur le topic général de la conversation
            messagingTemplate.convertAndSend(
                    "/topic/conversation/" + conversation.getId(),
                    messageDTO
            );

        } catch (Exception e) {
            log.error("Error broadcasting message: {}", e.getMessage());
        }
    }

    private void sendPushNotificationsToOfflineUsers(Conversation conversation, Message message, UserResponse sender) {
        conversation.getParticipants().stream()
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
        String senderAvatar = null;
        if (sender != null && sender.profileImageUrl() != null) {
            senderAvatar = sender.profileImageUrl();
        }

        return MessageDTO.builder()
                .id(message.getId())
                .conversationId(message.getConversation().getId())
                .senderId(message.getSenderId())
                .senderName(message.getSenderName())
                .senderAvatar(senderAvatar)
                .content(message.getContent())
                .type(message.getType().name())
                .status(message.getStatus().name())
                .attachmentUrl(message.getAttachmentUrl())
                .sentAt(message.getSentAt())
                .readAt(message.getReadAt())
                .editedAt(message.getEditedAt())
                .isDeleted(message.isDeleted())
                .canEdit(message.getSenderId().equals(sender != null ? sender.id() : null))
                .canDelete(message.getSenderId().equals(sender != null ? sender.id() : null))
                .build();
    }
}