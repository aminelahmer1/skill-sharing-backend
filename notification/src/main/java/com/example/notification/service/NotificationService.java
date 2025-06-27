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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final UserServiceClient userServiceClient;
    private final JavaMailSender mailSender;
    private final KeycloakTokenService keycloakTokenService;
    private final SimpMessagingTemplate messagingTemplate;

    @KafkaListener(topics = "notifications", groupId = "notification-group")
    public void handleNotification(NotificationEvent event) {
        if (event == null || event.type() == null) {
            log.error("Événement de notification invalide reçu");
            return;
        }

        UserResponse producer = fetchUser(event.producerId());
        UserResponse receiver = fetchUser(event.receiverId());

        if (producer == null || receiver == null) {
            log.error("Échec de la récupération des détails pour producerId: {} ou receiverId: {}", event.producerId(), event.receiverId());
            return;
        }

        String receiverFullName = receiver.firstName() + " " + receiver.lastName();

        switch (event.type()) {
            case "EXCHANGE_CREATED":
                sendNotification(producer, event, String.format("Le receveur %s a demandé à rejoindre votre compétence %s.", receiverFullName, event.skillName()), "Notification de Partage de Compétence");
                break;
            case "EXCHANGE_ACCEPTED":
                sendNotification(receiver, event, String.format("Votre demande pour la compétence %s a été acceptée.", event.skillName()), "Notification de Partage de Compétence");
                break;
            case "EXCHANGE_REJECTED":
                sendNotification(receiver, event, String.format("Votre demande pour la compétence %s a été rejetée. Raison : %s", event.skillName(), event.reason() != null ? event.reason() : "Non spécifiée"), "Notification de Partage de Compétence");
                break;
            default:
                log.warn("Type de notification inconnu: {}", event.type());
        }
    }

    private UserResponse fetchUser(Long userId) {
        try {
            String token = keycloakTokenService.getAccessToken();
            UserResponse user = userServiceClient.getUserById(userId, token);
            if (user == null || user.keycloakId() == null || user.email() == null) {
                log.error("Données utilisateur invalides pour userId: {}", userId);
                return null;
            }
            return user;
        } catch (Exception e) {
            log.error("Erreur lors de la récupération de l'utilisateur avec l'ID {}: {}", userId, e.getMessage(), e);
            return null;
        }
    }

    private void sendNotification(UserResponse user, NotificationEvent event, String message, String subject) {
        if (user == null || user.keycloakId() == null) {
            log.error("User invalide pour l'envoi de notification");
            return;
        }

        Notification notification = createNotification(event, user.keycloakId(), message);
        notification = notificationRepository.save(notification);
        log.info("Notification sauvegardée: {}", notification);

        messagingTemplate.convertAndSendToUser(user.keycloakId(), "/queue/notifications", notification);

        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo(user.email());
        mailMessage.setSubject(subject);
        mailMessage.setText(message);

        try {
            mailSender.send(mailMessage);
            notification.setSent(true);
            notificationRepository.save(notification);
            log.info("Email envoyé à {} pour le type d'événement {}", user.email(), event.type());
            messagingTemplate.convertAndSendToUser(user.keycloakId(), "/queue/notifications", notification);
        } catch (MailException e) {
            log.error("Échec de l'envoi de l'email à {} pour le type d'événement {}: {}", user.email(), event.type(), e.getMessage(), e);
        }
    }

    private Notification createNotification(NotificationEvent event, String userId, String message) {
        Notification notification = new Notification();
        notification.setType(event.type());
        notification.setExchangeId(event.exchangeId());
        notification.setUserId(userId);
        notification.setMessage(message);
        notification.setCreatedAt(LocalDateTime.now());
        notification.setSent(false);
        notification.setRead(false);
        return notification;
    }

    public List<Notification> getNotificationsByUserId(String userId) {
        List<Notification> notifications = notificationRepository.findByUserId(userId);
        List<Notification> filteredNotifications = notifications.stream()
                .filter(Objects::nonNull)
                .filter(n -> n.getId() != null && n.getUserId() != null && n.getMessage() != null && n.getCreatedAt() != null && n.getType() != null)
                .collect(Collectors.toList());
        log.info("Notifications filtrées pour userId {}: {}", userId, filteredNotifications);
        return filteredNotifications;
    }

    public Notification markAsRead(Long id, Jwt jwt) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification non trouvée"));

        String authenticatedUserId = jwt.getClaimAsString("sub");
        if (!authenticatedUserId.equals(notification.getUserId())) {
            throw new RuntimeException("Non autorisé à marquer cette notification comme lue");
        }

        notification.setRead(true);
        Notification updated = notificationRepository.save(notification);
        log.info("Notification marquée comme lue: {}", updated);
        messagingTemplate.convertAndSendToUser(authenticatedUserId, "/queue/notifications", updated);
        return updated;
    }

    public void markAllAsRead(String userId) {
        List<Notification> notifications = notificationRepository.findByUserId(userId);
        for (Notification notification : notifications.stream().filter(Objects::nonNull).toList()) {
            if (!notification.isRead()) {
                notification.setRead(true);
                Notification updated = notificationRepository.save(notification);
                log.info("Notification marquée comme lue dans markAllAsRead: {}", updated);
                messagingTemplate.convertAndSendToUser(userId, "/queue/notifications", updated);
            }
        }
    }
}