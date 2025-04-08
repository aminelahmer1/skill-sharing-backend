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

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

    @Column(name = "skill_id", nullable = false)
    private Integer skillId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "provider_rating")
    private Integer providerRating;

    @Column(nullable = false)
    private String status;

    public void setStatus(String status) {
        if (!ExchangeStatus.isValid(status)) {
            throw new InvalidStatusException("Invalid status: " + status);
        }
        this.status = status;
    }
}