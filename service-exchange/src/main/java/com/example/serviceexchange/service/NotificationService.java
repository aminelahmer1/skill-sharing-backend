package com.example.serviceexchange.service;

import com.example.serviceexchange.dto.SkillResponse;
import com.example.serviceexchange.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final WebSocketService webSocketService;
    private final EmailService emailService;
    private final FirebaseMessagingService firebaseMessagingService;

    public void notifyNewRequest(UserResponse producer, UserResponse receiver, SkillResponse skill) {
        String message = String.format(
                "New request from %s to join your skill '%s'",
                receiver.username(),
                skill.name()
        );

        // Notification WebSocket
        webSocketService.sendNotification(producer.keycloakId(), message);

        // Notification email
        emailService.sendEmail(
                producer.email(),
                "New Skill Request",
                message
        );

        // Notification push
        firebaseMessagingService.sendNotification(
                producer.keycloakId(),
                "New Request",
                message
        );
    }

    public void notifyRequestAccepted(UserResponse receiver, UserResponse producer, SkillResponse skill) {
        String message = String.format(
                "Your request to join '%s' has been accepted by %s",
                skill.name(),
                producer.username()
        );

        webSocketService.sendNotification(receiver.keycloakId(), message);
        emailService.sendEmail(receiver.email(), "Request Accepted", message);
    }

    public void notifyRequestRejected(UserResponse receiver, UserResponse producer, SkillResponse skill, String reason) {
        String message = String.format(
                "Your request to join '%s' was rejected by %s. Reason: %s",
                skill.name(),
                producer.username(),
                reason != null ? reason : "Not specified"
        );

        webSocketService.sendNotification(receiver.keycloakId(), message);
        emailService.sendEmail(receiver.email(), "Request Rejected", message);
    }

    public void notifySessionStarted(UserResponse receiver, UserResponse producer, Integer skillId) {
        String message = String.format(
                "The session for skill #%d is starting now with %s",
                skillId,
                producer.username()
        );

        webSocketService.sendNotification(receiver.keycloakId(), message);
        emailService.sendEmail(receiver.email(), "Session Started", message);
    }

    public void notifySessionCompleted(UserResponse receiver, UserResponse producer, Integer skillId) {
        String message = String.format(
                "The session for skill #%d with %s has been completed. Please provide your feedback.",
                skillId,
                producer.username()
        );

        webSocketService.sendNotification(receiver.keycloakId(), message);
        emailService.sendEmail(receiver.email(), "Session Completed", message);
    }
}