package com.example.servicelivestream.service;

import com.example.servicelivestream.dto.ChatMessage;
import com.example.servicelivestream.dto.TypingIndicator;
import com.example.servicelivestream.dto.UserResponse;
import com.example.servicelivestream.entity.ChatMessageEntity;
import com.example.servicelivestream.entity.LivestreamSession;
import com.example.servicelivestream.feignclient.UserServiceClient;
import com.example.servicelivestream.repository.ChatMessageRepository;
import com.example.servicelivestream.repository.LivestreamSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {
    private final LivestreamSessionRepository sessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserServiceClient userServiceClient;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public ChatMessage processAndSaveMessage(Long sessionId, ChatMessage message, Jwt jwt) {
        LivestreamSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found with ID: " + sessionId));

        String token = "Bearer " + jwt.getTokenValue();
        UserResponse user = userServiceClient.getUserByKeycloakId(jwt.getSubject(), token);

        // Vérifier l'autorisation
        if (!session.getProducerId().equals(user.id()) && !session.getReceiverIds().contains(user.id())) {
            throw new SecurityException("User not authorized to send messages in session: " + sessionId);
        }

        // Créer le message avec les informations de l'utilisateur
        ChatMessage validatedMessage = new ChatMessage(
                user.id().toString(),
                getUserDisplayName(user),
                message.message(),
                LocalDateTime.now()
        );

        // Sauvegarder en base de données
        ChatMessageEntity messageEntity = ChatMessageEntity.builder()
                .session(session)
                .userId(user.id())
                .username(getUserDisplayName(user))
                .content(message.message())
                .timestamp(validatedMessage.timestamp())
                .build();

        chatMessageRepository.save(messageEntity);

        log.info("Message saved for session {} by user {}: {}", sessionId, user.id(), message.message());

        return validatedMessage;
    }

    public TypingIndicator processTypingIndicator(Long sessionId, TypingIndicator indicator, Jwt jwt) {
        // Vérifier que la session existe
        LivestreamSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found with ID: " + sessionId));

        String token = "Bearer " + jwt.getTokenValue();
        UserResponse user = userServiceClient.getUserByKeycloakId(jwt.getSubject(), token);

        // Vérifier l'autorisation
        if (!session.getProducerId().equals(user.id()) && !session.getReceiverIds().contains(user.id())) {
            throw new SecurityException("User not authorized for session: " + sessionId);
        }

        // Créer l'indicateur de frappe avec les informations de l'utilisateur
        TypingIndicator validatedIndicator = new TypingIndicator(
                user.id().toString(),
                getUserDisplayName(user),
                indicator.isTyping(),
                LocalDateTime.now()
        );

        log.debug("Typing indicator for session {} by user {}: {}",
                sessionId, user.id(), indicator.isTyping());

        return validatedIndicator;
    }

    @Transactional
    public void sendMessage(Long sessionId, ChatMessage message, Jwt jwt) {
        ChatMessage processedMessage = processAndSaveMessage(sessionId, message, jwt);
        messagingTemplate.convertAndSend("/topic/session/" + sessionId + "/chat", processedMessage);
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getMessagesForSession(Long sessionId, Jwt jwt) {
        LivestreamSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found with ID: " + sessionId));

        String token = "Bearer " + jwt.getTokenValue();
        UserResponse user = userServiceClient.getUserByKeycloakId(jwt.getSubject(), token);

        // Vérifier l'autorisation
        if (!session.getProducerId().equals(user.id()) && !session.getReceiverIds().contains(user.id())) {
            throw new SecurityException("User not authorized to view messages in session: " + sessionId);
        }

        return chatMessageRepository.findBySessionIdOrderByTimestampAsc(sessionId).stream()
                .map(entity -> new ChatMessage(
                        entity.getUserId().toString(),
                        entity.getUsername(),
                        entity.getContent(),
                        entity.getTimestamp()
                ))
                .toList();
    }

    private String getUserDisplayName(UserResponse user) {
        if (user.firstName() != null && user.lastName() != null) {
            return user.firstName() + " " + user.lastName();
        } else if (user.firstName() != null) {
            return user.firstName();
        } else if (user.username() != null) {
            return user.username();
        } else {
            return "User " + user.id();
        }
    }
}