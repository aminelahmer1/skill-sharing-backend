package com.example.serviceexchange.repository;

import com.example.serviceexchange.entity.Exchange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExchangeRepository extends JpaRepository<Exchange, Integer> {
    List<Exchange> findByProducerId(Long producerId);
    List<Exchange> findByReceiverId(Long receiverId);
    List<Exchange> findBySkillId(Integer skillId);
    List<Exchange> findBySkillIdAndStatus(Integer skillId, String status);
    List<Exchange> findByProducerIdOrReceiverId(Long producerId, Long receiverId);

    @Query("SELECT e FROM Exchange e WHERE e.producerId = :userId OR e.receiverId = :userId ORDER BY e.streamingDate DESC")
    List<Exchange> findUserExchanges(@Param("userId") Long userId);

    @Query("SELECT COUNT(e) > 0 FROM Exchange e WHERE e.producerId = :producerId AND e.receiverId = :receiverId AND e.skillId = :skillId")
    boolean existsByProducerIdAndReceiverIdAndSkillId(
            @Param("producerId") Long producerId,
            @Param("receiverId") Long receiverId,
            @Param("skillId") Integer skillId);

    List<Exchange> findByProducerIdAndStatus(Long producerId, String status);

    @Query("SELECT e FROM Exchange e WHERE e.status = :status AND e.streamingDate BETWEEN :start AND :end")
    List<Exchange> findByStatusAndStreamingDateBetween(
            @Param("status") String status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
    List<Exchange> findByReceiverIdAndStatus(Long receiverId, String status);
    @Query("SELECT e FROM Exchange e WHERE e.skillId = :skillId AND (e.producerId = :userId OR e.receiverId = :userId)")
    List<Exchange> findBySkillIdAndUserId(@Param("skillId") Integer skillId, @Param("userId") Long userId);

    // Add method for receiver check in getExchangesBySkillId
    @Query("SELECT e FROM Exchange e WHERE e.skillId = :skillId AND e.receiverId = :receiverId")
    Optional<Exchange> findBySkillIdAndReceiverId(@Param("skillId") Integer skillId, @Param("receiverId") Long receiverId);
}