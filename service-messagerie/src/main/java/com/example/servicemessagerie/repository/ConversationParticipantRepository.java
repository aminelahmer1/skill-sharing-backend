package com.example.servicemessagerie.repository;

import com.example.servicemessagerie.entity.ConversationParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, Long> {

    Optional<ConversationParticipant> findByConversationIdAndUserId(Long conversationId, Long userId);

    boolean existsByConversationIdAndUserId(Long conversationId, Long userId);

    @Query("SELECT p FROM ConversationParticipant p WHERE p.conversation.id = :conversationId AND p.isActive = true")
    List<ConversationParticipant> findActiveParticipantsByConversationId(@Param("conversationId") Long conversationId);

}