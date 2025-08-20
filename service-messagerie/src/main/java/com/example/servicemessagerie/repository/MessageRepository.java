package com.example.servicemessagerie.repository;

import com.example.servicemessagerie.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * Trouve les messages d'une conversation (non supprimés) avec pagination
     */
    Page<Message> findByConversationIdAndIsDeletedFalse(Long conversationId, Pageable pageable);

    /**
     * Trouve le dernier message d'une conversation
     */
    Optional<Message> findTopByConversationIdOrderBySentAtDesc(Long conversationId);

    /**
     * Trouve les messages non lus pour un utilisateur dans une conversation
     */
    @Query("SELECT m FROM Message m WHERE m.conversation.id = :conversationId " +
            "AND m.senderId != :userId AND m.status != 'READ' " +
            "AND m.isDeleted = false ORDER BY m.sentAt ASC")
    List<Message> findUnreadMessagesForUser(@Param("conversationId") Long conversationId,
                                            @Param("userId") Long userId);

    /**
     * Compte les messages non lus pour un utilisateur (toutes conversations)
     */
    @Query("SELECT COUNT(m) FROM Message m " +
            "JOIN m.conversation.participants p " +
            "WHERE p.userId = :userId AND p.isActive = true " +
            "AND m.senderId != :userId " +
            "AND m.status != 'read' AND m.isDeleted = false")
    int countUnreadMessagesForUser(@Param("userId") Long userId);

    /**
     * Compte les messages non lus dans une conversation spécifique
     */
    @Query("SELECT COUNT(m) FROM Message m WHERE m.conversation.id = :conversationId " +
            "AND m.senderId != :userId AND m.status != 'read' AND m.isDeleted = false")
    int countUnreadInConversation(@Param("conversationId") Long conversationId,
                                  @Param("userId") Long userId);

    /**
     * Compte les messages non lus par conversation pour un utilisateur
     */
    @Query("SELECT m.conversation.id, COUNT(m) FROM Message m " +
            "JOIN m.conversation.participants p " +
            "WHERE p.userId = :userId AND p.isActive = true " +
            "AND m.senderId != :userId " +
            "AND m.status != 'read' AND m.isDeleted = false " +
            "GROUP BY m.conversation.id")
    List<Object[]> countUnreadMessagesPerConversation(@Param("userId") Long userId);

    /**
     * Recherche des messages par contenu dans une conversation
     */
    Page<Message> findByConversationIdAndContentContainingIgnoreCaseAndIsDeletedFalse(
            Long conversationId, String content, Pageable pageable);

    /**
     * Trouve les messages d'un utilisateur dans une conversation
     */
    @Query("SELECT m FROM Message m WHERE m.conversation.id = :conversationId " +
            "AND m.senderId = :userId AND m.isDeleted = false " +
            "ORDER BY m.sentAt DESC")
    List<Message> findByConversationIdAndSenderId(@Param("conversationId") Long conversationId,
                                                  @Param("userId") Long userId);

    /**
     * Trouve les messages récents d'une conversation (dernières 24h)
     */
    @Query("SELECT m FROM Message m WHERE m.conversation.id = :conversationId " +
            "AND m.sentAt >= :since AND m.isDeleted = false " +
            "ORDER BY m.sentAt DESC")
    List<Message> findRecentMessages(@Param("conversationId") Long conversationId,
                                     @Param("since") LocalDateTime since);

    /**
     * Trouve tous les messages non lus d'une conversation après un certain message
     */
    @Query("SELECT m FROM Message m WHERE m.conversation.id = :conversationId " +
            "AND m.id > :lastReadMessageId AND m.senderId != :userId " +
            "AND m.isDeleted = false ORDER BY m.sentAt ASC")
    List<Message> findUnreadMessagesSince(@Param("conversationId") Long conversationId,
                                          @Param("lastReadMessageId") Long lastReadMessageId,
                                          @Param("userId") Long userId);

    /**
     * Compte le nombre total de messages dans une conversation
     */
    @Query("SELECT COUNT(m) FROM Message m WHERE m.conversation.id = :conversationId " +
            "AND m.isDeleted = false")
    long countByConversationId(@Param("conversationId") Long conversationId);

    /**
     * Trouve les messages avec des pièces jointes dans une conversation
     */
    @Query("SELECT m FROM Message m WHERE m.conversation.id = :conversationId " +
            "AND m.attachmentUrl IS NOT NULL AND m.isDeleted = false " +
            "ORDER BY m.sentAt DESC")
    List<Message> findMessagesWithAttachments(@Param("conversationId") Long conversationId);

    /**
     * Trouve les messages d'un type spécifique dans une conversation
     */
    @Query("SELECT m FROM Message m WHERE m.conversation.id = :conversationId " +
            "AND m.type = :messageType AND m.isDeleted = false " +
            "ORDER BY m.sentAt DESC")
    List<Message> findByConversationIdAndType(@Param("conversationId") Long conversationId,
                                              @Param("messageType") Message.MessageType messageType);

    /**
     * Trouve les messages modifiés dans une conversation
     */
    @Query("SELECT m FROM Message m WHERE m.conversation.id = :conversationId " +
            "AND m.editedAt IS NOT NULL AND m.isDeleted = false " +
            "ORDER BY m.editedAt DESC")
    List<Message> findEditedMessages(@Param("conversationId") Long conversationId);

    /**
     * Trouve les messages entre deux dates
     */
    @Query("SELECT m FROM Message m WHERE m.conversation.id = :conversationId " +
            "AND m.sentAt BETWEEN :startDate AND :endDate " +
            "AND m.isDeleted = false ORDER BY m.sentAt ASC")
    List<Message> findMessagesBetweenDates(@Param("conversationId") Long conversationId,
                                           @Param("startDate") LocalDateTime startDate,
                                           @Param("endDate") LocalDateTime endDate);

    /**
     * Supprime physiquement les messages anciens (pour le nettoyage)
     */
    @Query("DELETE FROM Message m WHERE m.isDeleted = true " +
            "AND m.sentAt < :cutoffDate")
    void deleteOldDeletedMessages(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Trouve les conversations avec des messages récents pour un utilisateur
     */
    @Query("SELECT DISTINCT m.conversation.id FROM Message m " +
            "JOIN m.conversation.participants p " +
            "WHERE p.userId = :userId AND p.isActive = true " +
            "AND m.sentAt >= :since AND m.isDeleted = false")
    List<Long> findConversationIdsWithRecentMessages(@Param("userId") Long userId,
                                                     @Param("since") LocalDateTime since);

    /**
     * Statistiques: compte les messages par conversation pour un utilisateur
     */
    @Query("SELECT m.conversation.id, COUNT(m) FROM Message m " +
            "JOIN m.conversation.participants p " +
            "WHERE p.userId = :userId AND p.isActive = true " +
            "AND m.isDeleted = false " +
            "GROUP BY m.conversation.id")
    List<Object[]> countMessagesByConversationForUser(@Param("userId") Long userId);

    /**
     * Trouve les messages mentionnant un utilisateur (si implémentation de mentions)
     */
    @Query("SELECT m FROM Message m WHERE m.conversation.id = :conversationId " +
            "AND m.content LIKE %:mention% AND m.isDeleted = false " +
            "ORDER BY m.sentAt DESC")
    List<Message> findMessagesWithMention(@Param("conversationId") Long conversationId,
                                          @Param("mention") String mention);

    /**
     * Marque tous les messages d'une conversation comme lus pour un utilisateur
     */
    @Query("UPDATE Message m SET m.status = 'READ', m.readAt = :readTime " +
            "WHERE m.conversation.id = :conversationId " +
            "AND m.senderId != :userId AND m.status != 'read'")
    int markAllAsRead(@Param("conversationId") Long conversationId,
                      @Param("userId") Long userId,
                      @Param("readTime") LocalDateTime readTime);
}