package com.example.servicelivestream.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordingRequest {
    private String format;
    private String quality;
    private Boolean includeChat;

    public String format() {
        return format != null ? format : "mp4";
    }
}