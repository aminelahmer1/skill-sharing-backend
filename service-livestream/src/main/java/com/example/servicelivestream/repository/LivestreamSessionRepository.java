package com.example.servicelivestream.repository;

import com.example.servicelivestream.entity.LivestreamSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LivestreamSessionRepository extends JpaRepository<LivestreamSession, Long> {
    // Existing method
    LivestreamSession findByRoomName(String roomName);
    @Query("SELECT s FROM LivestreamSession s WHERE s.status = :status AND s.startTime <= :currentTime")
    List<LivestreamSession> findByStatusAndStartTimeBefore(
            @Param("status") String status,
            @Param("currentTime") LocalDateTime currentTime
    );
    // New derived query method to find sessions by skillId and a list of statuses
    List<LivestreamSession> findBySkillIdAndStatusIn(Integer skillId, List<String> statuses);


    // Recherche par producerId
    List<LivestreamSession> findByProducerId(Long producerId);

    // Recherche où receiverId est dans la liste des receivers
    @Query("SELECT ls FROM LivestreamSession ls WHERE :receiverId MEMBER OF ls.receiverIds")
    List<LivestreamSession> findByReceiverIdsContaining(@Param("receiverId") Long receiverId);

    // Recherche où receiverId est dans la liste et avec un statut spécifique
    @Query("SELECT ls FROM LivestreamSession ls WHERE :receiverId MEMBER OF ls.receiverIds AND ls.status = :status")
    List<LivestreamSession> findByReceiverIdsContainingAndStatus(
            @Param("receiverId") Long receiverId,
            @Param("status") String status
    );

    // Sessions complétées par producerId
    @Query("SELECT ls FROM LivestreamSession ls WHERE ls.producerId = :producerId AND ls.status = 'COMPLETED' ORDER BY ls.endTime DESC")
    List<LivestreamSession> findCompletedByProducerId(@Param("producerId") Long producerId);

    // Sessions complétées par receiverId
    @Query("SELECT ls FROM LivestreamSession ls WHERE :receiverId MEMBER OF ls.receiverIds AND ls.status = 'COMPLETED' ORDER BY ls.endTime DESC")
    List<LivestreamSession> findCompletedByReceiverId(@Param("receiverId") Long receiverId);

    // Sessions par skillId
    List<LivestreamSession> findBySkillId(Integer skillId);

    // Sessions par producerId et status
    List<LivestreamSession> findByProducerIdAndStatus(Long producerId, String status);
}