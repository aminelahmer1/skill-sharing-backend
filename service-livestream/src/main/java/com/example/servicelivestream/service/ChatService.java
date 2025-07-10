package com.example.servicelivestream.service;

import com.example.servicelivestream.dto.ChatMessage;
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
    public void sendMessage(Long sessionId, ChatMessage message, Jwt jwt) {
        LivestreamSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found with ID: " + sessionId));

        String token = "Bearer " + jwt.getTokenValue();
        UserResponse user = userServiceClient.getUserByKeycloakId(jwt.getSubject(), token);
        if (!session.getProducerId().equals(user.id()) && !session.getReceiverIds().contains(user.id())) {
            throw new SecurityException("User not authorized to send messages in session: " + sessionId);
        }

        ChatMessage validatedMessage = new ChatMessage(
                user.id().toString(),
                user.firstName() + " " + user.lastName(),
                message.message(),
                LocalDateTime.now()
        );
        ChatMessageEntity messageEntity = ChatMessageEntity.builder()
                .session(session)
                .userId(user.id())
                .username(user.firstName() + " " + user.lastName())
                .content(message.message())
                .timestamp(validatedMessage.timestamp())
                .build();
        chatMessageRepository.save(messageEntity);

        messagingTemplate.convertAndSend("/topic/session/" + sessionId + "/chat", validatedMessage);
        log.info("Message sent to session {} by user {}: {}", sessionId, user.id(), message.message());
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getMessagesForSession(Long sessionId, Jwt jwt) {
        LivestreamSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found with ID: " + sessionId));
        UserResponse user = userServiceClient.getUserByKeycloakId(jwt.getSubject(), "Bearer " + jwt.getTokenValue());
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
}