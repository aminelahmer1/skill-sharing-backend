package com.example.servicemessagerie.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ParticipantDTO {
    private Long userId;
    private String userName;
    private String role; // ADMIN, MEMBER
    private boolean isOnline;
    private String avatar;
    private LocalDateTime lastSeen;
}