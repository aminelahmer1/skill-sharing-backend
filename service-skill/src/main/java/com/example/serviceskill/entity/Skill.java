package com.example.serviceskill.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "skills", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(updatable = false)
    private Integer id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "available_quantity", nullable = false)
    private Integer availableQuantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "nb_inscrits", nullable = false)
    @Builder.Default
    private Integer nbInscrits = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "category_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_skill_category")
    )
    private Category category;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    private Integer version;

    // Méthode pour incrémenter les inscriptions
    public void incrementRegistrations() {
        if (nbInscrits >= availableQuantity) {
            throw new IllegalStateException("No available slots remaining");
        }
        nbInscrits++;
    }
}