package com.example.servicelivestream.dto;

public record WebhookEvent(
        String action,
        String streamId,
        String recordingFilePath
) {}