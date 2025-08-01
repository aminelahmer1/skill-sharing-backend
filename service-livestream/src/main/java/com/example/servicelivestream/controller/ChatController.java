package com.example.servicelivestream.controller;

import com.example.servicelivestream.dto.ChatMessage;
import com.example.servicelivestream.dto.TypingIndicator;
import com.example.servicelivestream.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @MessageMapping("/session/{sessionId}/chat")
    @SendTo("/topic/session/{sessionId}/chat")
    @PreAuthorize("hasRole('RECEIVER') or hasRole('PRODUCER')")
    public ChatMessage sendMessage(
            @DestinationVariable Long sessionId,
            @Payload ChatMessage message,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        // Récupération du JWT depuis l'authentification
        JwtAuthenticationToken auth = (JwtAuthenticationToken) headerAccessor.getUser();
        if (auth == null) {
            log.error("No authentication found in WebSocket message");
            throw new SecurityException("Authentication required");
        }

        Jwt jwt = auth.getToken();
        log.debug("Processing chat message for session {} from user {}", sessionId, jwt.getSubject());

        return chatService.processAndSaveMessage(sessionId, message, jwt);
    }
    @PreAuthorize("hasRole('RECEIVER') or hasRole('PRODUCER')")
    @MessageMapping("/session/{sessionId}/typing")
    @SendTo("/topic/session/{sessionId}/typing")

    public TypingIndicator handleTyping(
            @DestinationVariable Long sessionId,
            @Payload TypingIndicator indicator,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        JwtAuthenticationToken auth = (JwtAuthenticationToken) headerAccessor.getUser();
        if (auth == null) {
            log.error("No authentication found in WebSocket message");
            throw new SecurityException("Authentication required");
        }

        Jwt jwt = auth.getToken();
        log.debug("Processing typing indicator for session {} from user {}", sessionId, jwt.getSubject());

        return chatService.processTypingIndicator(sessionId, indicator, jwt);
    }
    @PreAuthorize("hasRole('RECEIVER') or hasRole('PRODUCER')")
    @MessageMapping("/session/{sessionId}/join")
    @SendTo("/topic/session/{sessionId}/users")
    public String userJoined(
            @DestinationVariable Long sessionId,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        JwtAuthenticationToken auth = (JwtAuthenticationToken) headerAccessor.getUser();
        if (auth == null) {
            log.error("No authentication found in WebSocket message");
            throw new SecurityException("Authentication required");
        }

        String userId = auth.getToken().getSubject();
        log.info("User {} joined session {}", userId, sessionId);

        return "User joined: " + userId;
    }
    @PreAuthorize("hasRole('RECEIVER') or hasRole('PRODUCER')")
    @MessageMapping("/session/{sessionId}/leave")
    @SendTo("/topic/session/{sessionId}/users")
    public String userLeft(
            @DestinationVariable Long sessionId,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        JwtAuthenticationToken auth = (JwtAuthenticationToken) headerAccessor.getUser();
        if (auth == null) {
            return "Unknown user left";
        }

        String userId = auth.getToken().getSubject();
        log.info("User {} left session {}", userId, sessionId);

        return "User left: " + userId;
    }
}