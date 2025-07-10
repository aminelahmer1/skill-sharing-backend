package com.example.servicemessagerie.controller;


import com.example.servicemessagerie.dto.ChatMessage;
import com.example.servicemessagerie.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;

    @MessageMapping("/session/{sessionId}/chat")
    public void sendMessage(
            @DestinationVariable Long sessionId,
            @Payload ChatMessage message,
            @AuthenticationPrincipal Jwt jwt
    ) {
        chatService.sendMessage(sessionId, message, jwt);
    }
}