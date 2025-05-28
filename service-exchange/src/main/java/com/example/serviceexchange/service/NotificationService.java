package com.example.serviceexchange.service;

import com.example.serviceexchange.dto.NotificationEvent;
import com.example.serviceexchange.dto.SkillResponse;
import com.example.serviceexchange.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    private final WebSocketService webSocketService;
    private final EmailService emailService;
    private final FirebaseMessagingService firebaseMessagingService;
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    public void notifyNewRequest(UserResponse producer, UserResponse receiver, SkillResponse skill, Integer exchangeId) {
        String message = String.format(
                "Nouvelle demande de %s pour rejoindre votre compétence '%s'",
                receiver.username(), skill.name()
        );

        sendRealTimeNotifications(producer.keycloakId(), producer.email(), "Nouvelle Demande de Compétence", message);

        NotificationEvent event = new NotificationEvent(
                "EXCHANGE_CREATED",
                exchangeId,
                producer.id(),
                receiver.id(),
                skill.name(),
                null,
                skill.streamingDate()
        );
        sendKafkaNotification(event);
    }

    public void notifyRequestAccepted(UserResponse receiver, UserResponse producer, SkillResponse skill, Integer exchangeId) {
        String message = String.format(
                "Votre demande pour rejoindre '%s' a été acceptée par %s",
                skill.name(), producer.username()
        );

        sendRealTimeNotifications(receiver.keycloakId(), receiver.email(), "Demande Acceptée", message);

        NotificationEvent event = new NotificationEvent(
                "EXCHANGE_ACCEPTED",
                exchangeId,
                producer.id(),
                receiver.id(),
                skill.name(),
                null,
                null
        );
        sendKafkaNotification(event);
    }

    public void notifyRequestRejected(UserResponse receiver, UserResponse producer, SkillResponse skill, String reason, Integer exchangeId) {
        String message = String.format(
                "Votre demande pour rejoindre '%s' a été rejetée par %s. Raison : %s",
                skill.name(), producer.username(), reason != null ? reason : "Non spécifiée"
        );

        sendRealTimeNotifications(receiver.keycloakId(), receiver.email(), "Demande Rejetée", message);

        NotificationEvent event = new NotificationEvent(
                "EXCHANGE_REJECTED",
                exchangeId,
                producer.id(),
                receiver.id(),
                skill.name(),
                reason,
                null
        );
        sendKafkaNotification(event);
    }

    public void notifySessionStarted(UserResponse receiver, UserResponse producer, Integer skillId, Integer exchangeId) {
        String message = String.format(
                "La session pour la compétence #%d commence maintenant avec %s",
                skillId, producer.username()
        );

        sendRealTimeNotifications(receiver.keycloakId(), receiver.email(), "Session Commencée", message);

        NotificationEvent event = new NotificationEvent(
                "SESSION_STARTED",
                exchangeId,
                producer.id(),
                receiver.id(),
                "Compétence #" + skillId,
                null,
                null
        );
        sendKafkaNotification(event);
    }

    public void notifySessionCompleted(UserResponse receiver, UserResponse producer, Integer skillId, Integer exchangeId) {
        String message = String.format(
                "La session pour la compétence #%d avec %s est terminée. Veuillez fournir votre avis.",
                skillId, producer.username()
        );

        sendRealTimeNotifications(receiver.keycloakId(), receiver.email(), "Session Terminée", message);

        NotificationEvent event = new NotificationEvent(
                "SESSION_COMPLETED",
                exchangeId,
                producer.id(),
                receiver.id(),
                "Compétence #" + skillId,
                null,
                null
        );
        sendKafkaNotification(event);
    }

    private void sendRealTimeNotifications(String keycloakId, String email, String subject, String message) {
        try {
            webSocketService.sendNotification(keycloakId, message);
            emailService.sendEmail(email, subject, message);
            firebaseMessagingService.sendNotification(keycloakId, subject, message);
        } catch (Exception e) {
            log.error("Échec de l'envoi de la notification en temps réel à {} : {}", email, e.getMessage(), e);
        }
    }

    private void sendKafkaNotification(NotificationEvent event) {
        try {
            kafkaTemplate.send("notifications", event);
            log.info("Notification Kafka envoyée pour le type d'événement : {}", event.type());
        } catch (Exception e) {
            log.error("Échec de l'envoi de la notification Kafka pour le type d'événement {} : {}", event.type(), e.getMessage(), e);
        }
    }
}