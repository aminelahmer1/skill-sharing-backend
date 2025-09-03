package com.example.notification.controller;

import com.example.notification.entity.Notification;
import com.example.notification.service.NotificationService;
import com.example.notification.dto.NotificationEvent;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.kafka.core.KafkaTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('RECEIVER') or hasRole('PRODUCER')")
    public ResponseEntity<List<Notification>> getUserNotifications(
            @PathVariable String userId,
            @AuthenticationPrincipal Jwt jwt) {
        String authenticatedUserId = jwt.getClaimAsString("sub");
        if (!authenticatedUserId.equals(userId)) {
            return ResponseEntity.status(403).build();
        }
        List<Notification> notifications = notificationService.getNotificationsByUserId(userId);
        return notifications.isEmpty() ? ResponseEntity.noContent().build() : ResponseEntity.ok(notifications);
    }

    @PutMapping("/{id}/read")
    @PreAuthorize("hasRole('RECEIVER') or hasRole('PRODUCER')")
    public ResponseEntity<Notification> markNotificationAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        try {
            Notification updated = notificationService.markAsRead(id, jwt);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            if (e.getMessage().contains("non trouvée") || e.getMessage().contains("Non autorisé")) {
                return ResponseEntity.status(403).build();
            }
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping("/user/{userId}/mark-all-read")
    @PreAuthorize("hasRole('RECEIVER') or hasRole('PRODUCER')")
    public ResponseEntity<Void> markAllAsRead(
            @PathVariable String userId,
            @AuthenticationPrincipal Jwt jwt) {
        String authenticatedUserId = jwt.getClaimAsString("sub");
        if (!authenticatedUserId.equals(userId)) {
            return ResponseEntity.status(403).build();
        }
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/user/{userId}/unread-count")
    @PreAuthorize("hasRole('RECEIVER') or hasRole('PRODUCER')")
    public ResponseEntity<Map<String, Integer>> getUnreadCount(
            @PathVariable String userId,
            @AuthenticationPrincipal Jwt jwt) {
        String authenticatedUserId = jwt.getClaimAsString("sub");
        if (!authenticatedUserId.equals(userId)) {
            return ResponseEntity.status(403).build();
        }

        List<Notification> notifications = notificationService.getNotificationsByUserId(userId);
        long unreadCount = notifications.stream().filter(n -> !n.isRead()).count();

        return ResponseEntity.ok(Map.of("unreadCount", (int) unreadCount));
    }





    // Endpoint de test pour le développement (à désactiver en production)
    @PostMapping("/test/{userId}")
    @PreAuthorize("hasRole('ADMIN')") // Seulement pour les admins
    public ResponseEntity<Map<String, String>> sendTestNotification(
            @PathVariable String userId,
            @RequestParam String type,
            @RequestParam(required = false) String message) {

        log.info("Sending test notification to user: {} of type: {}", userId, type);

        try {
            // Créer un événement de test
            NotificationEvent testEvent = new NotificationEvent(
                    type,
                    1, // Exchange ID fictif
                    1L, // Producer ID fictif
                    1L, // Receiver ID fictif
                    "Test Skill",
                    message != null ? message : "Ceci est une notification de test",
                    LocalDateTime.now().toString()
            );

            // Envoyer via Kafka
            kafkaTemplate.send("notifications", testEvent);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Test notification sent",
                    "type", type,
                    "userId", userId
            ));

        } catch (Exception e) {
            log.error("Error sending test notification", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    // Endpoint pour obtenir les types de notifications disponibles
    @GetMapping("/types")
    public ResponseEntity<List<String>> getNotificationTypes() {
        List<String> types = List.of(
                "EXCHANGE_CREATED",
                "EXCHANGE_ACCEPTED",
                "EXCHANGE_REJECTED",
                "24_HOUR_REMINDER",
                "1_HOUR_REMINDER",
                "LIVESTREAM_STARTED",
                "SESSION_SCHEDULED",
                "SESSION_COMPLETED"
        );
        return ResponseEntity.ok(types);
    }
}