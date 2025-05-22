package com.example.serviceexchange.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WebSocketService {
    private final SimpMessagingTemplate messagingTemplate;

    public void sendNotification(String userId, String message) {
        messagingTemplate.convertAndSendToUser(
                userId,
                "/queue/notifications",
                new NotificationMessage(message)
        );
    }

    @Getter
    @AllArgsConstructor
    private static class NotificationMessage {
        private final String content;
    }
}