package com.example.servicemessagerie.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "conversation_participants")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationParticipant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_name")
    private String userName;

    @Enumerated(EnumType.STRING)
    private ParticipantRole role = ParticipantRole.MEMBER;

    @CreationTimestamp
    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    @Column(name = "notification_enabled")
    private boolean notificationEnabled = true;

    @Column(name = "is_active")
    private boolean isActive = true;

    public enum ParticipantRole {
        ADMIN, MEMBER
    }
}