package com.example.servicemessagerie.service;


import com.example.servicemessagerie.dto.ChatMessage;
import com.example.servicemessagerie.dto.UserResponse;
import com.example.servicemessagerie.entity.LivestreamSession;
import com.example.servicemessagerie.feignclient.UserServiceClient;
import com.example.servicemessagerie.repository.LivestreamSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {
    private final LivestreamSessionRepository sessionRepository;
    private final UserServiceClient userServiceClient;
    private final SimpMessagingTemplate messagingTemplate;

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
                message.message()
        );

        messagingTemplate.convertAndSend("/topic/session/" + sessionId + "/chat", validatedMessage);
        log.info("Message sent to session {} by user {}: {}", sessionId, user.id(), message.message());
    }
}