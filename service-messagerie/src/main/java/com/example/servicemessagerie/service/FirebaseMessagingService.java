package com.example.servicemessagerie.service;

import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FirebaseMessagingService {

    private final FirebaseMessaging firebaseMessaging;

    public void sendMessageNotification(Long userId, String senderName, String message, Long conversationId) {
        try {
            // Créer la notification
            Notification notification = Notification.builder()
                    .setTitle("Nouveau message de " + senderName)
                    .setBody(message)
                    .build();

            // Données supplémentaires
            Map<String, String> data = new HashMap<>();
            data.put("type", "NEW_MESSAGE");
            data.put("conversationId", conversationId.toString());
            data.put("senderName", senderName);

            // Créer le message
            Message firebaseMessage = Message.builder()
                    .setNotification(notification)
                    .putAllData(data)
                    .setTopic("user_" + userId) // L'utilisateur doit s'abonner à ce topic
                    .build();

            // Envoyer
            String response = firebaseMessaging.send(firebaseMessage);
            log.info("Message notification sent successfully: {}", response);

        } catch (Exception e) {
            log.error("Error sending message notification", e);
        }
    }

    public void sendTypingIndicator(Long conversationId, Long userId, String userName, boolean isTyping) {
        try {
            Map<String, String> data = new HashMap<>();
            data.put("type", "TYPING_INDICATOR");
            data.put("conversationId", conversationId.toString());
            data.put("userId", userId.toString());
            data.put("userName", userName);
            data.put("isTyping", String.valueOf(isTyping));

            Message message = Message.builder()
                    .putAllData(data)
                    .setTopic("conversation_" + conversationId)
                    .build();

            firebaseMessaging.send(message);

        } catch (Exception e) {
            log.error("Error sending typing indicator", e);
        }
    }

    public void sendPresenceUpdate(Long userId, boolean isOnline) {
        try {
            Map<String, String> data = new HashMap<>();
            data.put("type", "PRESENCE_UPDATE");
            data.put("userId", userId.toString());
            data.put("isOnline", String.valueOf(isOnline));

            // Envoyer à tous les contacts de l'utilisateur
            Message message = Message.builder()
                    .putAllData(data)
                    .setTopic("contacts_" + userId)
                    .build();

            firebaseMessaging.send(message);

        } catch (Exception e) {
            log.error("Error sending presence update", e);
        }
    }
}