package com.example.servicemessagerie.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UnreadCountDTO {
    private int totalUnread;
    private int directMessages;
    private int groupMessages;
    private int skillMessages;
}