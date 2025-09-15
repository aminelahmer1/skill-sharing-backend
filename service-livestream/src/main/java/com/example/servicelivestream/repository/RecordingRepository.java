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

    // CHANGÉ: Retourne maintenant une List au lieu d'Optional
    @Query("SELECT r FROM Recording r WHERE r.session.id = :sessionId AND r.status = :status")
    List<Recording> findBySessionIdAndStatus(@Param("sessionId") Long sessionId, @Param("status") String status);

    @Query("SELECT COUNT(r) FROM Recording r WHERE r.session.id = :sessionId")
    long countBySessionId(@Param("sessionId") Long sessionId);

    @Query("SELECT r FROM Recording r WHERE r.createdAt < :cutoffDate")
    List<Recording> findByCreatedAtBefore(@Param("cutoffDate") LocalDateTime cutoffDate);

    Optional<Recording> findBySession(LivestreamSession session);

    List<Recording> findByAuthorizedUsersContaining(Long userId);

    // Pour trouver les enregistrements bloqués depuis longtemps
    List<Recording> findByStatusAndStartedAtBefore(String status, LocalDateTime threshold);

    // Pour vérifier l'existence d'un enregistrement actif
    @Query("SELECT COUNT(r) > 0 FROM Recording r WHERE r.session.id = :sessionId AND r.status = 'RECORDING'")
    boolean hasActiveRecording(@Param("sessionId") Long sessionId);
}