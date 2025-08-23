package com.example.servicemessagerie.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Entity
@Table(name = "conversations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"participants", "messages"}) // ✅ CRITIQUE - Évite ConcurrentModificationException
@ToString(exclude = {"participants", "messages"}) // ✅ CRITIQUE - Évite lazy loading dans toString
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

    @Column(name = "last_message", length = 1000)
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

    // ✅ CORRECTION - Collections avec fetch LAZY obligatoire
    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<ConversationParticipant> participants = new HashSet<>();

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("sentAt DESC")
    @Builder.Default
    private Set<Message> messages = new HashSet<>();

    // ✅ ENUMS
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

    // ✅ CORRECTION CRITIQUE - isParticipant SANS accès direct à la collection
    public boolean isParticipant(Long userId) {
        // ⚠️ IMPORTANT: Cette méthode ne doit PAS être utilisée directement
        // Utiliser ConversationParticipantRepository.existsByConversationIdAndUserId() à la place
        log.warn("isParticipant() called directly - use repository method instead");
        return false;
    }

    // ✅ MÉTHODES UTILITAIRES THREAD-SAFE
    public synchronized void updateLastMessage(String content) {
        if (content != null && content.length() > 1000) {
            this.lastMessage = content.substring(0, 997) + "...";
        } else {
            this.lastMessage = content;
        }
        this.lastMessageTime = LocalDateTime.now();
    }

    // ✅ GESTION DES PARTICIPANTS - SÉCURISÉE
    public synchronized void addParticipant(ConversationParticipant participant) {
        if (participant != null && participants != null) {
            try {
                participants.add(participant);
                participant.setConversation(this);
            } catch (Exception e) {
                log.warn("Error adding participant: {}", e.getMessage());
            }
        }
    }

    public synchronized void removeParticipant(ConversationParticipant participant) {
        if (participant != null && participants != null) {
            try {
                participants.remove(participant);
                if (participant.getConversation() == this) {
                    participant.setConversation(null);
                }
            } catch (Exception e) {
                log.warn("Error removing participant: {}", e.getMessage());
            }
        }
    }

    // ✅ MÉTHODES UTILITAIRES SÉCURISÉES
    public int getActiveParticipantCount() {
        // ⚠️ Ne pas utiliser directement - utiliser repository
        log.warn("getActiveParticipantCount() called - use repository count instead");
        return 0;
    }

    // ✅ MÉTHODES HELPER
    public boolean isDirectConversation() {
        return type == ConversationType.DIRECT;
    }

    public boolean isGroupConversation() {
        return type == ConversationType.GROUP;
    }

    public boolean isSkillConversation() {
        return type == ConversationType.SKILL_GROUP;
    }

    public boolean isActive() {
        return status == ConversationStatus.ACTIVE;
    }

    // ✅ LIFECYCLE CALLBACKS
    @PrePersist
    public void prePersist() {
        if (status == null) {
            status = ConversationStatus.ACTIVE;
        }
        if (version == null) {
            version = 0L;
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ✅ CUSTOM EQUALS/HASHCODE - ÉVITE PROBLÈMES HIBERNATE
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Conversation)) return false;
        Conversation that = (Conversation) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    // ✅ CUSTOM TOSTRING - ÉVITE LAZY LOADING
    @Override
    public String toString() {
        return String.format("Conversation{id=%d, name='%s', type=%s, status=%s, skillId=%d, lastMessageTime=%s}",
                id, name, type, status, skillId, lastMessageTime);
    }

    // ✅ MÉTHODES DE VALIDATION
    public boolean isValidForSending() {
        return this.id != null &&
                this.status == ConversationStatus.ACTIVE &&
                this.name != null &&
                !this.name.trim().isEmpty();
    }

    public boolean canUserSend(Long userId) {
        // Déléguer au service/repository
        return this.isActive() && userId != null;
    }

    // ✅ MÉTHODES UTILITAIRES POUR LE BUSINESS
    public String getDisplayName() {
        if (name == null || name.trim().isEmpty()) {
            return "Conversation " + (id != null ? id : "");
        }
        return name.trim();
    }

    public String getTypeDisplayName() {
        switch (type) {
            case DIRECT: return "Message direct";
            case GROUP: return "Groupe";
            case SKILL_GROUP: return "Compétence";
            default: return "Conversation";
        }
    }

    public String getStatusDisplayName() {
        switch (status) {
            case ACTIVE: return "Active";
            case ARCHIVED: return "Archivée";
            case COMPLETED: return "Terminée";
            case CANCELLED: return "Annulée";
            default: return "Inconnue";
        }
    }
}