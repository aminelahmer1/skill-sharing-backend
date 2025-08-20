package com.example.servicemessagerie.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "conversations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConversationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ConversationStatus status = ConversationStatus.ACTIVE;

    @Column(name = "skill_id")
    private Integer skillId;

    @Column(name = "last_message")
    private String lastMessage;

    @Column(name = "last_message_time")
    private LocalDateTime lastMessageTime;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<ConversationParticipant> participants = new HashSet<>();

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("sentAt DESC")
    @Builder.Default
    private Set<Message> messages = new HashSet<>();

    // Enums
    public enum ConversationType {
        DIRECT,      // Conversation directe entre 2 utilisateurs
        GROUP,       // Groupe de discussion
        SKILL_GROUP  // Groupe lié à une compétence
    }

    public enum ConversationStatus {
        ACTIVE,      // Conversation active
        ARCHIVED,    // Conversation archivée
        COMPLETED,   // Session de compétence terminée
        CANCELLED    // Conversation annulée
    }

    // Méthodes utilitaires
    public void updateLastMessage(String content) {
        this.lastMessage = content;
        this.lastMessageTime = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void addParticipant(ConversationParticipant participant) {
        participants.add(participant);
        participant.setConversation(this);
    }

    public void removeParticipant(ConversationParticipant participant) {
        participants.remove(participant);
        participant.setConversation(null);
    }

    public boolean isParticipant(Long userId) {
        return participants.stream()
                .anyMatch(p -> p.getUserId().equals(userId) && p.isActive());
    }

    public int getActiveParticipantCount() {
        return (int) participants.stream()
                .filter(ConversationParticipant::isActive)
                .count();
    }

    public boolean isDirectConversation() {
        return type == ConversationType.DIRECT;
    }

    public boolean isGroupConversation() {
        return type == ConversationType.GROUP;
    }

    public boolean isSkillConversation() {
        return type == ConversationType.SKILL_GROUP;
    }

    @PrePersist
    public void prePersist() {
        if (status == null) {
            status = ConversationStatus.ACTIVE;
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}