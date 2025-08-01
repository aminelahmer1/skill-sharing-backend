package com.example.servicelivestream.dto;

import java.time.LocalDateTime;

public record RecordingResponse(
        String recordingId,
        Long sessionId,
        String status,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String downloadUrl
) {}