package com.example.servicelivestream.repository;

import com.example.servicelivestream.entity.Recording;
import com.example.servicelivestream.entity.LivestreamSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RecordingRepository extends JpaRepository<Recording, Long> {

    @Query("SELECT r FROM Recording r WHERE r.session.id = :sessionId ORDER BY r.recordingNumber ASC")
    List<Recording> findBySessionIdOrderByRecordingNumberAsc(@Param("sessionId") Long sessionId);

    @Query("SELECT r FROM Recording r WHERE r.session.id = :sessionId AND r.status = :status")
    Optional<Recording> findBySessionIdAndStatus(@Param("sessionId") Long sessionId, @Param("status") String status);

    @Query("SELECT COUNT(r) FROM Recording r WHERE r.session.id = :sessionId")
    long countBySessionId(@Param("sessionId") Long sessionId);

    @Query("SELECT r FROM Recording r WHERE r.createdAt < :cutoffDate")
    List<Recording> findByCreatedAtBefore(@Param("cutoffDate") LocalDateTime cutoffDate);

    Optional<Recording> findBySession(LivestreamSession session);
    List<Recording> findByAuthorizedUsersContaining(Long userId);

}