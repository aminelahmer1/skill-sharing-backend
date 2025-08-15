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

    // NOUVEAU: Rating donné par le receiver
    @Column(name = "receiver_rating")
    private Integer receiverRating;

    // NOUVEAU: Commentaire du receiver
    @Column(name = "receiver_comment", length = 500)
    private String receiverComment;

    // NOUVEAU: Date du rating
    @Column(name = "rating_date")
    private LocalDateTime ratingDate;

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

    // NOUVEAU: Méthode pour définir le rating
    public void setReceiverRating(Integer rating, String comment) {
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
        this.receiverRating = rating;
        this.receiverComment = comment;
        this.ratingDate = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}