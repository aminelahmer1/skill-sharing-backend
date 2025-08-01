package com.example.servicelivestream.dto;

public record RecordingRequest(
        String format,
        String quality,
        boolean includeChat
) {
    public RecordingRequest() {
        this("mp4", "high", false);
    }
}