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
    @Query("SELECT s FROM LivestreamSession s WHERE s.status = :status AND s.startTime < :currentTime")
    List<LivestreamSession> findByStatusAndStartTimeBefore(
            @Param("status") String status,
            @Param("currentTime") LocalDateTime currentTime
    );
    // New derived query method to find sessions by skillId and a list of statuses
    List<LivestreamSession> findBySkillIdAndStatusIn(Integer skillId, List<String> statuses);
}