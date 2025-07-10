package com.example.servicelivestream.controller;

import com.example.servicelivestream.dto.ChatMessage;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class ChatController {
    @MessageMapping("/session/{sessionId}/chat")
    @SendTo("/topic/session/{sessionId}/chat")
    public ChatMessage sendMessage(
            @DestinationVariable Long sessionId,
            ChatMessage message
    ) {
        return message;
    }
}