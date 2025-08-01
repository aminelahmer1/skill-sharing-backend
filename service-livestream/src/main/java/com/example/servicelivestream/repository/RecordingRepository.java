package com.example.servicelivestream.repository;

import com.example.servicelivestream.entity.LivestreamSession;
import com.example.servicelivestream.entity.Recording;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RecordingRepository extends JpaRepository<Recording, Long> {

    List<Recording> findByAuthorizedUsersContaining(Long userId);

    Optional<Recording> findBySession(LivestreamSession session);

    @Query("SELECT r FROM Recording r WHERE r.session.id = :sessionId")
    Optional<Recording> findBySessionId(@Param("sessionId") Long sessionId);

    List<Recording> findByCreatedAtBefore(LocalDateTime cutoffDate);

    @Query("SELECT r FROM Recording r WHERE r.session.producerId = :producerId")
    List<Recording> findByProducerId(@Param("producerId") Long producerId);

    @Query("SELECT COUNT(r) FROM Recording r WHERE r.session.producerId = :producerId")
    long countByProducerId(@Param("producerId") Long producerId);
}