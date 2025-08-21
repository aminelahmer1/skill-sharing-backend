package com.example.servicemessagerie.repository;

import com.example.servicemessagerie.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /**
     * ✅ CORRIGÉ: Requête optimisée pour éviter le warning Hibernate
     * Sépare la récupération des conversations et leurs participants
     */
    @Query("SELECT DISTINCT c FROM Conversation c " +
            "WHERE c.id IN (" +
            "    SELECT DISTINCT p.conversation.id FROM ConversationParticipant p " +
            "    WHERE p.userId = :userId AND p.isActive = true" +
            ") " +
            "AND c.status = 'ACTIVE' " +
            "ORDER BY c.lastMessageTime DESC NULLS LAST, c.createdAt DESC")
    Page<Conversation> findByParticipantUserId(@Param("userId") Long userId, Pageable pageable);

    /**
     * ✅ NOUVEAU: Méthode pour récupérer les conversations avec participants
     */
    @Query("SELECT DISTINCT c FROM Conversation c " +
            "LEFT JOIN FETCH c.participants p " +
            "WHERE c.id IN :conversationIds")
    List<Conversation> findConversationsWithParticipants(@Param("conversationIds") List<Long> conversationIds);

    /**
     * ✅ CORRIGÉ: Requête simple pour debug
     */
    @Query("SELECT c FROM Conversation c " +
            "JOIN c.participants p " +
            "WHERE p.userId = :userId AND p.isActive = true " +
            "ORDER BY c.lastMessageTime DESC NULLS LAST")
    List<Conversation> findByParticipantUserIdSimple(@Param("userId") Long userId);

    /**
     * Trouve toutes les conversations d'un utilisateur avec un statut spécifique
     */
    @Query("SELECT DISTINCT c FROM Conversation c " +
            "JOIN c.participants p " +
            "WHERE p.userId = :userId " +
            "AND c.status = :status " +
            "ORDER BY c.lastMessageTime DESC NULLS LAST")
    Page<Conversation> findByParticipantUserIdAndStatus(@Param("userId") Long userId,
                                                        @Param("status") Conversation.ConversationStatus status,
                                                        Pageable pageable);

    /**
     * Trouve une conversation directe entre deux utilisateurs
     */
    @Query("SELECT c FROM Conversation c " +
            "WHERE c.type = 'DIRECT' " +
            "AND EXISTS (SELECT p FROM c.participants p WHERE p.userId = :userId1 AND p.isActive = true) " +
            "AND EXISTS (SELECT p FROM c.participants p WHERE p.userId = :userId2 AND p.isActive = true)")
    Optional<Conversation> findDirectConversationBetweenUsers(@Param("userId1") Long userId1,
                                                              @Param("userId2") Long userId2);

    /**
     * Trouve une conversation de groupe pour une compétence
     */
    Optional<Conversation> findBySkillIdAndType(Integer skillId, Conversation.ConversationType type);

    /**
     * Trouve la conversation de compétence pour un utilisateur spécifique
     */
    @Query("SELECT c FROM Conversation c " +
            "JOIN c.participants p " +
            "WHERE c.type = 'SKILL_GROUP' AND c.skillId = :skillId " +
            "AND p.userId = :userId AND p.isActive = true")
    Optional<Conversation> findSkillGroupForUser(@Param("skillId") Integer skillId,
                                                 @Param("userId") Long userId);

    /**
     * Recherche des conversations par nom et utilisateur
     */
    @Query("SELECT DISTINCT c FROM Conversation c " +
            "JOIN c.participants p " +
            "WHERE p.userId = :userId AND p.isActive = true " +
            "AND c.status = 'ACTIVE' " +
            "AND LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "ORDER BY c.lastMessageTime DESC")
    List<Conversation> searchByNameAndUserId(@Param("userId") Long userId,
                                             @Param("query") String query);

    /**
     * ✅ NOUVEAU: Méthode de debug pour vérifier les participants d'un utilisateur
     */
    @Query("SELECT p FROM ConversationParticipant p WHERE p.userId = :userId")
    List<Object> findAllParticipantsByUserId(@Param("userId") Long userId);

    /**
     * ✅ NOUVEAU: Compter les conversations d'un utilisateur
     */
    @Query("SELECT COUNT(DISTINCT c) FROM Conversation c " +
            "JOIN c.participants p " +
            "WHERE p.userId = :userId AND p.isActive = true")
    long countConversationsByUserId(@Param("userId") Long userId);

    /**
     * Trouve toutes les conversations d'un utilisateur par type
     */
    @Query("SELECT DISTINCT c FROM Conversation c " +
            "JOIN c.participants p " +
            "WHERE p.userId = :userId AND p.isActive = true " +
            "AND c.type = :type AND c.status = 'ACTIVE' " +
            "ORDER BY c.lastMessageTime DESC")
    List<Conversation> findByUserIdAndType(@Param("userId") Long userId,
                                           @Param("type") Conversation.ConversationType type);

    /**
     * Compte le nombre de conversations actives d'un utilisateur
     */
    @Query("SELECT COUNT(DISTINCT c) FROM Conversation c " +
            "JOIN c.participants p " +
            "WHERE p.userId = :userId AND p.isActive = true " +
            "AND c.status = 'ACTIVE'")
    long countActiveConversationsByUserId(@Param("userId") Long userId);

    /**
     * Trouve les conversations avec des messages non lus pour un utilisateur
     */
    @Query("SELECT DISTINCT c FROM Conversation c " +
            "JOIN c.participants p " +
            "JOIN c.messages m " +
            "WHERE p.userId = :userId AND p.isActive = true " +
            "AND c.status = 'ACTIVE' " +
            "AND m.senderId != :userId " +
            "AND m.status != 'READ' AND m.isDeleted = false " +
            "ORDER BY c.lastMessageTime DESC")
    List<Conversation> findConversationsWithUnreadMessages(@Param("userId") Long userId);

    /**
     * Trouve toutes les conversations liées à une compétence spécifique
     */
    @Query("SELECT c FROM Conversation c WHERE c.skillId = :skillId")
    List<Conversation> findBySkillId(@Param("skillId") Integer skillId);

    /**
     * Trouve les conversations récentes d'un utilisateur (limite)
     */
    @Query("SELECT DISTINCT c FROM Conversation c " +
            "JOIN c.participants p " +
            "WHERE p.userId = :userId AND p.isActive = true " +
            "AND c.status = 'ACTIVE' " +
            "ORDER BY c.lastMessageTime DESC")
    List<Conversation> findRecentConversations(@Param("userId") Long userId, Pageable pageable);

    /**
     * Vérifie si un utilisateur peut accéder à une conversation
     */
    @Query("SELECT COUNT(p) > 0 FROM Conversation c " +
            "JOIN c.participants p " +
            "WHERE c.id = :conversationId AND p.userId = :userId " +
            "AND p.isActive = true AND c.status = 'ACTIVE'")
    boolean canUserAccessConversation(@Param("conversationId") Long conversationId,
                                      @Param("userId") Long userId);
}