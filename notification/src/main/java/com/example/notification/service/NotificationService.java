package com.example.notification.service;

import com.example.notification.entity.Notification;
import com.example.notification.repository.NotificationRepository;
import com.example.notification.dto.NotificationEvent;  // Import local
import com.example.notification.client.UserServiceClient;  // Import local
import com.example.notification.dto.UserResponse;  // Import local
import com.example.notification.client.SkillServiceClient;  // Import local
import com.example.notification.dto.SkillResponse;  // Import local
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
import java.time.format.DateTimeFormatter;
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
    private final SkillServiceClient skillServiceClient;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm");

    @KafkaListener(topics = "notifications", groupId = "notification-group")
    public void handleNotification(NotificationEvent event) {
        if (event == null || event.type() == null) {
            log.error("Événement de notification invalide reçu");
            return;
        }

        log.info("Processing notification event type: {}", event.type());

        UserResponse producer = fetchUser(event.producerId());
        UserResponse receiver = fetchUser(event.receiverId());

        if (producer == null || receiver == null) {
            log.error("Échec de la récupération des détails pour producerId: {} ou receiverId: {}",
                    event.producerId(), event.receiverId());
            return;
        }

        String receiverFullName = receiver.firstName() + " " + receiver.lastName();
        String producerFullName = producer.firstName() + " " + producer.lastName();

        // Récupérer le nom du skill si nécessaire
        String skillName = event.skillName();
        if (skillName != null && skillName.matches("\\d+")) {
            // Si skillName contient un ID numérique, récupérer le vrai nom
            try {
                Integer skillId = Integer.parseInt(skillName);
                SkillResponse skill = skillServiceClient.getSkillById(skillId);
                if (skill != null) {
                    skillName = skill.name();
                }
            } catch (Exception e) {
                log.warn("Could not fetch skill name for ID: {}", skillName);
            }
        }

        switch (event.type()) {
            case "EXCHANGE_CREATED":
                sendNotification(producer, event,
                        String.format("Le receveur %s a demandé à rejoindre votre compétence %s.",
                                receiverFullName, skillName),
                        "Nouvelle demande de partage de compétence",
                        skillName);
                break;

            case "EXCHANGE_ACCEPTED":
                sendNotification(receiver, event,
                        String.format("Votre demande pour la compétence %s a été acceptée par %s.",
                                skillName, producerFullName),
                        "Demande acceptée - " + skillName,
                        skillName);
                break;

            case "EXCHANGE_REJECTED":
                sendNotification(receiver, event,
                        String.format("Votre demande pour la compétence %s a été rejetée. Raison : %s",
                                skillName, event.reason() != null ? event.reason() : "Non spécifiée"),
                        "Demande refusée - " + skillName,
                        skillName);
                break;

            case "24_HOUR_REMINDER":
                // Rappel 24h pour les deux parties
                String message24h = String.format(
                        "📅 Rappel : La session de livestream pour la compétence '%s' est prévue à %s",
                        skillName,
                        formatStreamingDate(event.streamingDate())
                );
                sendNotification(producer, event, message24h, "📅 Rappel - Session demain", skillName);
                sendNotification(receiver, event, message24h, "📅 Rappel - Session demain", skillName);
                break;

            case "1_HOUR_REMINDER":
                // Rappel 1h pour les deux parties
                String message1h = String.format(
                        "⏰ Rappel urgent : La session de livestream pour la compétence '%s' commence dans 1 heure !",
                        skillName
                );
                sendNotification(producer, event, message1h, "⏰ Session dans 1 heure !", skillName);
                sendNotification(receiver, event, message1h, "⏰ Session dans 1 heure !", skillName);
                break;

            case "LIVESTREAM_STARTED":
                // CORRECTION: Notification UNIQUEMENT pour le receiver
                // Le producteur n'a pas besoin d'être notifié car c'est lui qui démarre le stream
                String messageLive = String.format(
                        "🔴 La session de livestream pour la compétence '%s' avec %s vient de commencer ! Rejoignez maintenant.",
                        skillName, producerFullName
                );
                sendNotification(receiver, event, messageLive, "🔴 Livestream en cours !", skillName);

                log.info("Livestream started notification sent to receiver {} for skill '{}'",
                        receiver.email(), skillName);
                break;

            case "SESSION_SCHEDULED":
                // Notification pour les deux parties quand une session est programmée
                String messageScheduled = String.format(
                        "📅 La session pour la compétence '%s' a été programmée pour le %s",
                        skillName,
                        formatStreamingDate(event.streamingDate())
                );
                sendNotification(receiver, event, messageScheduled, "📅 Session programmée", skillName);
                break;

            case "SESSION_COMPLETED":
                // Notification pour les deux parties quand une session est terminée
                String messageCompleted = String.format(
                        "✅ La session de livestream pour la compétence '%s' est maintenant terminée. Merci de votre participation !",
                        skillName
                );
                sendNotification(receiver, event, messageCompleted, "✅ Session terminée", skillName);
                break;

            default:
                log.warn("Type de notification inconnu: {}", event.type());
        }
    }
    private String formatStreamingDate(String dateStr) {
        try {
            if (dateStr == null) return "Date non définie";
            LocalDateTime date = LocalDateTime.parse(dateStr);
            return date.format(DATE_FORMATTER);
        } catch (Exception e) {
            return dateStr != null ? dateStr : "Date non définie";
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

    private void sendNotification(UserResponse user, NotificationEvent event, String message, String subject, String skillName) {
        if (user == null || user.keycloakId() == null) {
            log.error("User invalide pour l'envoi de notification");
            return;
        }

        // Sauvegarder la notification en base
        Notification notification = createNotification(event, user.keycloakId(), message);
        notification = notificationRepository.save(notification);
        log.info("Notification sauvegardée: {}", notification);

        // Envoyer via WebSocket
        try {
            messagingTemplate.convertAndSendToUser(user.keycloakId(), "/queue/notifications", notification);
            log.info("WebSocket notification envoyée à l'utilisateur: {}", user.keycloakId());
        } catch (Exception e) {
            log.error("Erreur envoi WebSocket: {}", e.getMessage());
        }

        // Envoyer l'email avec un formatage amélioré
        try {
            sendFormattedEmail(user, subject, message, event, skillName);
            notification.setSent(true);
            notificationRepository.save(notification);
            log.info("Email envoyé à {} pour le type d'événement {}", user.email(), event.type());
        } catch (MailException e) {
            log.error("Échec de l'envoi de l'email à {} pour le type d'événement {}: {}",
                    user.email(), event.type(), e.getMessage(), e);
        }
    }

    private void sendFormattedEmail(UserResponse user, String subject, String message, NotificationEvent event, String skillName) {
        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo(user.email());
        mailMessage.setSubject("SkillSharing - " + subject);

        // Créer un contenu d'email plus détaillé
        StringBuilder emailContent = new StringBuilder();
        emailContent.append("Bonjour ").append(user.firstName()).append(",\n\n");
        emailContent.append(message).append("\n\n");

        // Ajouter des détails supplémentaires selon le type
        if (event.type().contains("REMINDER")) {
            emailContent.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            emailContent.append("📍 Compétence : ").append(skillName).append("\n");
            if (event.streamingDate() != null) {
                emailContent.append("📅 Date et heure : ").append(formatStreamingDate(event.streamingDate())).append("\n");
            }
            emailContent.append("\n💡 Conseils pour la session :\n");
            emailContent.append("• Assurez-vous d'avoir une connexion internet stable\n");
            emailContent.append("• Préparez vos questions à l'avance\n");
            emailContent.append("• Connectez-vous 5 minutes avant le début\n");
            emailContent.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        }

        if ("LIVESTREAM_STARTED".equals(event.type())) {
            emailContent.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            emailContent.append("🔴 LE LIVESTREAM EST EN DIRECT MAINTENANT !\n");
            emailContent.append("📍 Compétence : ").append(skillName).append("\n");
            emailContent.append("\n⚡ Rejoignez la session immédiatement pour ne rien manquer !\n");
            emailContent.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        }

        emailContent.append("\n");
        emailContent.append("Cordialement,\n");
        emailContent.append("L'équipe SkillSharing\n\n");
        emailContent.append("---\n");
        emailContent.append("Ceci est un email automatique, merci de ne pas y répondre.");

        mailMessage.setText(emailContent.toString());
        mailSender.send(mailMessage);
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
        return notifications.stream()
                .filter(Objects::nonNull)
                .filter(n -> n.getId() != null && n.getUserId() != null &&
                        n.getMessage() != null && n.getCreatedAt() != null && n.getType() != null)
                .sorted((n1, n2) -> n2.getCreatedAt().compareTo(n1.getCreatedAt()))
                .collect(Collectors.toList());
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