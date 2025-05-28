package com.example.notification.service;

import com.example.notification.entity.Notification;
import com.example.notification.repository.NotificationRepository;
import com.example.serviceexchange.dto.NotificationEvent;
import com.example.serviceuser.client.UserServiceClient;
import com.example.serviceuser.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final UserServiceClient userServiceClient;
    private final JavaMailSender mailSender;
    private final KeycloakTokenService keycloakTokenService;

    @KafkaListener(topics = "notifications", groupId = "notification-group")
    public void handleNotification(NotificationEvent event) {
        if (event == null || event.type() == null) {
            log.error("Événement de notification reçu nul ou invalide");
            return;
        }

        UserResponse producer = fetchUser(event.producerId());
        UserResponse receiver = fetchUser(event.receiverId());

        if (producer == null || receiver == null) {
            log.error("Échec de la récupération des détails de l'utilisateur pour producerId: {} ou receiverId: {}",
                    event.producerId(), event.receiverId());
            return;
        }

        switch (event.type()) {
            case "EXCHANGE_CREATED":
                sendNotification(producer, event,
                        String.format("Le receveur %s a demandé à rejoindre votre compétence %s.",
                                receiver.username(), event.skillName()),
                        "Notification de Partage de Compétence");
                break;
            case "EXCHANGE_ACCEPTED":
                sendNotification(receiver, event,
                        String.format("Votre demande pour la compétence %s a été acceptée.", event.skillName()),
                        "Notification de Partage de Compétence");
                break;
            case "EXCHANGE_REJECTED":
                sendNotification(receiver, event,
                        String.format("Votre demande pour la compétence %s a été rejetée. Raison : %s",
                                event.skillName(), event.reason() != null ? event.reason() : "Non spécifiée"),
                        "Notification de Partage de Compétence");
                break;
            case "SESSION_REMINDER_24H":
            case "SESSION_REMINDER_1H":
                sendReminder(producer, event,
                        String.format("Préparez-vous pour votre session de la compétence %s le %s.",
                                event.skillName(), event.streamingDate()));
                sendReminder(receiver, event,
                        String.format("Rejoignez votre session pour la compétence %s le %s.",
                                event.skillName(), event.streamingDate()));
                break;
            case "SESSION_STARTED":
                sendNotification(receiver, event,
                        String.format("La diffusion en direct pour la compétence %s a commencé. Rejoignez maintenant !", event.skillName()),
                        "Notification de Partage de Compétence");
                break;
            case "SESSION_COMPLETED":
                sendNotification(receiver, event,
                        String.format("La session pour la compétence %s est terminée. Veuillez fournir votre avis.",
                                event.skillName()),
                        "Notification de Partage de Compétence");
                break;
            default:
                log.warn("Type de notification inconnu : {}", event.type());
                break;
        }
    }

    private UserResponse fetchUser(Long userId) {
        try {
            String token = keycloakTokenService.getAccessToken();
            return userServiceClient.getUserById(userId, token);
        } catch (Exception e) {
            log.error("Erreur lors de la récupération de l'utilisateur avec l'ID {} : {}", userId, e.getMessage(), e);
            return null;
        }
    }

    private void sendNotification(UserResponse user, NotificationEvent event, String message, String subject) {
        Notification notification = createNotification(event, user.id(), message);
        notificationRepository.save(notification);

        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo(user.email());
        mailMessage.setSubject(subject);
        mailMessage.setText(message);

        try {
            mailSender.send(mailMessage);
            notification.setSent(true);
            notificationRepository.save(notification);
            log.info("Notification envoyée à {} pour le type d'événement {}", user.email(), event.type());
        } catch (MailException e) {
            log.error("Échec de l'envoi de l'email à {} pour le type d'événement {} : {}",
                    user.email(), event.type(), e.getMessage(), e);
        }
    }

    private void sendReminder(UserResponse user, NotificationEvent event, String message) {
        Notification notification = createNotification(event, user.id(), message);
        notificationRepository.save(notification);

        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo(user.email());
        mailMessage.setSubject("Rappel de Session");
        mailMessage.setText(message);

        try {
            mailSender.send(mailMessage);
            notification.setSent(true);
            notificationRepository.save(notification);
            log.info("Rappel envoyé à {} pour le type d'événement {}", user.email(), event.type());
        } catch (MailException e) {
            log.error("Échec de l'envoi du rappel à {} pour le type d'événement {} : {}",
                    user.email(), event.type(), e.getMessage(), e);
        }
    }

    private Notification createNotification(NotificationEvent event, Long userId, String message) {
        Notification notification = new Notification();
        notification.setType(event.type());
        notification.setExchangeId(event.exchangeId());
        notification.setUserId(userId);
        notification.setMessage(message);
        notification.setCreatedAt(LocalDateTime.now());
        notification.setSent(false);
        return notification;
    }
}