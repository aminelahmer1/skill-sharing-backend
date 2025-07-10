package com.example.servicelivestream.repository;

import com.example.servicelivestream.entity.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {
    // Update to use the session relationship
    @Query("SELECT m FROM ChatMessageEntity m WHERE m.session.id = :sessionId ORDER BY m.timestamp ASC")
    List<ChatMessageEntity> findBySessionIdOrderByTimestampAsc(@Param("sessionId") Long sessionId);}