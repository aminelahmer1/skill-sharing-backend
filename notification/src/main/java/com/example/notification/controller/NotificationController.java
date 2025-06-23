package com.example.notification.controller;

import com.example.notification.entity.Notification;
import com.example.notification.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

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
}