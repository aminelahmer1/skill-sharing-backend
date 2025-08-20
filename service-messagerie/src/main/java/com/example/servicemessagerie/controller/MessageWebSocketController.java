package com.example.servicemessagerie.controller;

import com.example.servicemessagerie.dto.MessageDTO;
import com.example.servicemessagerie.dto.TypingIndicatorDTO;
import com.example.servicemessagerie.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class MessageWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageService messageService;

    @MessageMapping("/conversation/{conversationId}/send")
    public void sendMessage(
            @DestinationVariable Long conversationId,
            @Payload MessageDTO message,
            SimpMessageHeaderAccessor headerAccessor) {

        JwtAuthenticationToken auth = (JwtAuthenticationToken) headerAccessor.getUser();
        if (auth == null) {
            throw new SecurityException("Authentication required");
        }

        Jwt jwt = auth.getToken();
        Long userId = Long.parseLong(jwt.getSubject());

        // Diffuser le message à tous les participants
        messagingTemplate.convertAndSend(
                "/topic/conversation/" + conversationId,
                message
        );

        log.debug("Message sent to conversation {} by user {}", conversationId, userId);
    }

    @MessageMapping("/conversation/{conversationId}/typing")
    @SendTo("/topic/conversation/{conversationId}/typing")
    public TypingIndicatorDTO handleTyping(
            @DestinationVariable Long conversationId,
            @Payload TypingIndicatorDTO indicator,
            SimpMessageHeaderAccessor headerAccessor) {

        JwtAuthenticationToken auth = (JwtAuthenticationToken) headerAccessor.getUser();
        if (auth == null) {
            throw new SecurityException("Authentication required");
        }

        log.debug("Typing indicator for conversation {} from user {}",
                conversationId, indicator.getUserId());

        return indicator;
    }

    @MessageMapping("/presence")
    public void updatePresence(
            @Payload Map<String, Object> presence,
            SimpMessageHeaderAccessor headerAccessor) {

        JwtAuthenticationToken auth = (JwtAuthenticationToken) headerAccessor.getUser();
        if (auth == null) {
            throw new SecurityException("Authentication required");
        }

        Long userId = Long.parseLong(auth.getToken().getSubject());

        // Diffuser la mise à jour de présence aux contacts
        messagingTemplate.convertAndSend(
                "/topic/presence/" + userId,
                presence
        );

        log.debug("Presence updated for user {}: {}", userId, presence);
    }
}