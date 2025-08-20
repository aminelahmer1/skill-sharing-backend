package com.example.servicemessagerie.dto;

import lombok.Data;

import java.util.List;

@Data
public class AddParticipantRequest {
    private List<Long> userIds;
}