// Conversation.java - VERSION CORRIGÉE
package com.example.servicemessagerie.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version")
    @Builder.Default
    private Long version = 0L;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<ConversationParticipant> participants = new HashSet<>();

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("sentAt DESC")
    @Builder.Default
    private Set<Message> messages = new HashSet<>();

    // Enums
    public enum ConversationType {
        DIRECT,
        GROUP,
        SKILL_GROUP
    }

    public enum ConversationStatus {
        ACTIVE,
        ARCHIVED,
        COMPLETED,
        CANCELLED
    }

    // Méthodes utilitaires thread-safe
    public synchronized void updateLastMessage(String content) {
        this.lastMessage = content;
        this.lastMessageTime = LocalDateTime.now();
    }

    public synchronized void addParticipant(ConversationParticipant participant) {
        if (participant != null) {
            participants.add(participant);
            participant.setConversation(this);
        }
    }

    public synchronized void removeParticipant(ConversationParticipant participant) {
        if (participant != null) {
            participants.remove(participant);
            participant.setConversation(null);
        }
    }

    /**
     * ✅ CORRECTION: Méthode isParticipant corrigée pour éviter ConcurrentModificationException
     * Utilise stream() directement sans créer de copie HashSet
     */
    public boolean isParticipant(Long userId) {
        if (userId == null || participants == null) {
            return false;
        }

        try {
            // ✅ IMPORTANT: Ne pas créer de HashSet, utiliser stream directement
            // Hibernate peut charger la collection pendant l'itération
            return participants.stream()
                    .anyMatch(p -> p != null &&
                            p.getUserId() != null &&
                            p.getUserId().equals(userId) &&
                            p.isActive());
        } catch (Exception e) {
            // En cas d'erreur, log et retourner false
            System.err.println("Error checking participant status: " + e.getMessage());
            return false;
        }
    }

    /**
     * ✅ CORRECTION: Méthode alternative qui force le chargement complet avant l'itération
     */
    public boolean isParticipantSafe(Long userId) {
        if (userId == null || participants == null) {
            return false;
        }

        // Forcer l'initialisation complète de la collection
        int size = participants.size(); // Force Hibernate à charger la collection

        return participants.stream()
                .anyMatch(p -> p != null &&
                        p.getUserId() != null &&
                        p.getUserId().equals(userId) &&
                        p.isActive());
    }

    public synchronized int getActiveParticipantCount() {
        if (participants == null) {
            return 0;
        }

        try {
            // ✅ Utiliser stream directement sans copie
            return (int) participants.stream()
                    .filter(p -> p != null && p.isActive())
                    .count();
        } catch (Exception e) {
            System.err.println("Error counting active participants: " + e.getMessage());
            return 0;
        }
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
        if (version == null) {
            version = 0L;
        }
    }
}