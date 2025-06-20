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
        try {
            String authenticatedUserId = jwt.getClaimAsString("sub");
            if (!authenticatedUserId.equals(userId)) {
                return ResponseEntity.status(403).build(); // Forbidden if userId mismatch
            }
            List<Notification> notifications = notificationService.getNotificationsByUserId(userId);
            return notifications.isEmpty() ? ResponseEntity.noContent().build() : ResponseEntity.ok(notifications);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping("/{id}/read")
    @PreAuthorize("hasRole('RECEIVER') or hasRole('PRODUCER')")
    public ResponseEntity<Void> markNotificationAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        try {
            notificationService.markAsRead(id, jwt);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build(); // Invalid ID or notification
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build(); // Unauthorized access
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}