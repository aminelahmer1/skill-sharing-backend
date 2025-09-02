package com.example.servicelivestream.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordingResponse {
    private String recordingId;
    private Long sessionId;
    private String fileName;
    private String skillName;
    private Integer recordingNumber;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long duration;
    private Long fileSize;
    private String downloadUrl;
}