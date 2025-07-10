package com.example.servicelivestream.controller;

import com.example.servicelivestream.dto.WebhookEvent;
import com.example.servicelivestream.service.LivestreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class WebhookController {
    private final LivestreamService livestreamService;

    @PostMapping("/webhook")
    public void handleWebhook(@RequestBody WebhookEvent event) {
        livestreamService.handleMediaServerEvent(event);
    }
}