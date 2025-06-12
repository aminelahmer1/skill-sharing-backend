package com.example.notification.controller;

import com.example.notification.entity.Notification;
import com.example.notification.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Notification>> getUserNotifications(
            @PathVariable String userId,
            @AuthenticationPrincipal Jwt jwt) {
        String authenticatedUserId = jwt.getClaimAsString("sub");
        if (!authenticatedUserId.equals(userId)) {
            return ResponseEntity.status(403).build(); // Accès interdit
        }
        List<Notification> notifications = notificationService.getNotificationsByUserId(userId);
        return ResponseEntity.ok(notifications);
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markNotificationAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        notificationService.markAsRead(id, jwt);
        return ResponseEntity.ok().build();
    }
}