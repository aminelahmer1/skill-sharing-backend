package com.example.servicelivestream.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

import java.util.List;

@Entity
@Table(name = "livestream_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Cacheable
public class LivestreamSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_id", nullable = false)
    private Integer skillId;

    @Column(name = "producer_id", nullable = false)
    private Long producerId;

    @ElementCollection
    @CollectionTable(name = "livestream_session_receiver_ids", joinColumns = @JoinColumn(name = "livestream_session_id"))
    @Column(name = "receiver_ids")
    private List<Long> receiverIds;

    @Column(name = "room_name", nullable = false)
    private String roomName;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "producer_token", length = 2048)
    private String producerToken;

    @Column(name = "recording_path")
    private String recordingPath;
}