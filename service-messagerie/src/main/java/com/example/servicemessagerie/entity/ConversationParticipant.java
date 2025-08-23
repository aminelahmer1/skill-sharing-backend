package com.example.servicemessagerie.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "conversation_participants",
        indexes = {
                @Index(name = "idx_conversation_user", columnList = "conversation_id, user_id"),
                @Index(name = "idx_user_active", columnList = "user_id, is_active"),
                @Index(name = "idx_conversation_active", columnList = "conversation_id, is_active")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"conversation"}) // ✅ CRITIQUE - Évite les références circulaires
@ToString(exclude = {"conversation"}) // ✅ CRITIQUE - Évite lazy loading
public class ConversationParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) // ✅ OBLIGATOIRE - LAZY pour éviter N+1
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_name", length = 255)
    private String userName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    @Builder.Default
    private ParticipantRole role = ParticipantRole.MEMBER;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    @Column(name = "notification_enabled", nullable = false)
    @Builder.Default
    private boolean notificationEnabled = true;

    // ✅ CORRECTION CRITIQUE - Champ is_active avec contraintes
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    // ✅ ENUM
    public enum ParticipantRole {
        ADMIN("Administrateur"),
        MEMBER("Membre"),
        MODERATOR("Modérateur"),
        READONLY("Lecture seule");

        private final String displayName;

        ParticipantRole(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // ✅ MÉTHODES MÉTIER
    public boolean canSendMessages() {
        return isActive && role != ParticipantRole.READONLY;
    }

    public boolean canManageParticipants() {
        return isActive && (role == ParticipantRole.ADMIN || role == ParticipantRole.MODERATOR);
    }

    public boolean isAdmin() {
        return role == ParticipantRole.ADMIN;
    }

    public boolean isModerator() {
        return role == ParticipantRole.MODERATOR;
    }

    public void deactivate() {
        this.isActive = false;
        this.leftAt = LocalDateTime.now();
    }

    public void reactivate() {
        this.isActive = true;
        this.leftAt = null;
    }

    public void markMessageAsRead(Long messageId) {
        this.lastReadMessageId = messageId;
    }

    // ✅ MÉTHODES D'AFFICHAGE
    public String getDisplayName() {
        if (userName != null && !userName.trim().isEmpty()) {
            return userName.trim();
        }
        return "Utilisateur " + (userId != null ? userId : "inconnu");
    }

    public String getRoleDisplayName() {
        return role != null ? role.getDisplayName() : "Membre";
    }

    public String getStatusDisplayName() {
        if (!isActive) {
            return "Inactif";
        }
        return "Actif";
    }

    // ✅ VALIDATION
    public boolean isValid() {
        return userId != null &&
                conversation != null &&
                role != null &&
                joinedAt != null;
    }

    // ✅ LIFECYCLE CALLBACKS
    @PrePersist
    public void prePersist() {
        if (role == null) {
            role = ParticipantRole.MEMBER;
        }
        if (joinedAt == null) {
            joinedAt = LocalDateTime.now();
        }
        if (userName == null || userName.trim().isEmpty()) {
            userName = "Utilisateur " + userId;
        }
    }

    @PreUpdate
    public void preUpdate() {
        if (!isActive && leftAt == null) {
            leftAt = LocalDateTime.now();
        } else if (isActive && leftAt != null) {
            leftAt = null; // Réactivation
        }
    }

    // ✅ CUSTOM EQUALS/HASHCODE - BASÉ SUR LES CLÉS MÉTIER
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConversationParticipant)) return false;
        ConversationParticipant that = (ConversationParticipant) o;

        // Utiliser les IDs si disponibles, sinon les clés métier
        if (id != null && that.id != null) {
            return Objects.equals(id, that.id);
        }

        return Objects.equals(userId, that.userId) &&
                Objects.equals(conversation != null ? conversation.getId() : null,
                        that.conversation != null ? that.conversation.getId() : null);
    }

    @Override
    public int hashCode() {
        if (id != null) {
            return Objects.hash(id);
        }
        return Objects.hash(userId, conversation != null ? conversation.getId() : null);
    }

    // ✅ CUSTOM TOSTRING - SANS RÉFÉRENCES CIRCULAIRES
    @Override
    public String toString() {
        return String.format(
                "ConversationParticipant{id=%d, userId=%d, userName='%s', role=%s, isActive=%s, joinedAt=%s}",
                id, userId, userName, role, isActive, joinedAt
        );
    }

    // ✅ MÉTHODES UTILITAIRES STATIQUES
    public static ConversationParticipant createMember(Conversation conversation, Long userId, String userName) {
        return ConversationParticipant.builder()
                .conversation(conversation)
                .userId(userId)
                .userName(userName)
                .role(ParticipantRole.MEMBER)
                .isActive(true)
                .notificationEnabled(true)
                .build();
    }

    public static ConversationParticipant createAdmin(Conversation conversation, Long userId, String userName) {
        return ConversationParticipant.builder()
                .conversation(conversation)
                .userId(userId)
                .userName(userName)
                .role(ParticipantRole.ADMIN)
                .isActive(true)
                .notificationEnabled(true)
                .build();
    }

    // ✅ HELPERS POUR LA LOGIQUE MÉTIER
    public boolean hasReadMessage(Long messageId) {
        return lastReadMessageId != null &&
                messageId != null &&
                lastReadMessageId.compareTo(messageId) >= 0;
    }

    public long getDaysSinceJoined() {
        if (joinedAt == null) return 0;
        return java.time.temporal.ChronoUnit.DAYS.between(joinedAt.toLocalDate(), LocalDateTime.now().toLocalDate());
    }

    public boolean isRecentJoin() {
        return getDaysSinceJoined() <= 7; // Moins d'une semaine
    }
}