package com.example.serviceexchange.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import com.example.serviceexchange.exception.InvalidStatusException;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "exchanges")
public class Exchange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "producer_id", nullable = false)
    private Long producerId;

    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

    @Column(name = "skill_id", nullable = false)
    private Integer skillId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "producer_rating")
    private Integer producerRating;

    @Column(nullable = false)
    private String status;

    @Column(name = "streaming_date")
    private LocalDateTime streamingDate;


    @Column(name = "rejection_reason")
    private String rejectionReason;
    public void setStatus(String status) {
        if (!ExchangeStatus.isValid(status)) {
            throw new InvalidStatusException("Invalid status: " + status);
        }
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }
}