package com.example.serviceexchange.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarEventResponse {
    private Integer id;
    private Integer skillId;
    private String skillName;
    private String skillDescription;
    private Long producerId;
    private String producerName;
    private Long receiverId;
    private String receiverName;
    private String status;
    private LocalDateTime streamingDate;
    private String streamingTime;
    private BigDecimal price;
    private String categoryName;
    private String role; // PRODUCER or RECEIVER
    private String eventType; // pending, accepted, scheduled, live, completed, rejected, cancelled
    private String color; // Color for calendar display
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Additional fields for calendar display
    public String getTitle() {
        if ("PRODUCER".equals(role)) {
            return skillName + " - " + receiverName;
        } else {
            return skillName + " - " + producerName;
        }
    }

    public String getDescription() {
        return String.format("%s\nStatut: %s\n%s",
                skillDescription != null ? skillDescription : "",
                getStatusLabel(),
                "PRODUCER".equals(role) ? "Apprenant: " + receiverName : "Formateur: " + producerName
        );
    }

    public String getStatusLabel() {
        return switch (status) {
            case "PENDING" -> "En attente";
            case "ACCEPTED" -> "Accepté";
            case "SCHEDULED" -> "Planifié";
            case "IN_PROGRESS" -> "En cours";
            case "COMPLETED" -> "Terminé";
            case "REJECTED" -> "Refusé";
            case "CANCELLED" -> "Annulé";
            default -> status;
        };
    }

    public boolean isLive() {
        return "IN_PROGRESS".equals(status);
    }

    public boolean isUpcoming() {
        return streamingDate != null && streamingDate.isAfter(LocalDateTime.now())
                && ("ACCEPTED".equals(status) || "SCHEDULED".equals(status));
    }

    public boolean isPast() {
        return streamingDate != null && streamingDate.isBefore(LocalDateTime.now());
    }
}