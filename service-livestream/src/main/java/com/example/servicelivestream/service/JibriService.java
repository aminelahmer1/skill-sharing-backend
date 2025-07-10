package com.example.servicelivestream.service;
/*
import com.example.servicelivestream.config.JitsiConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class JibriService {

    private final JitsiConfig jitsiConfig;
    private final RestTemplate restTemplate;

    @Autowired
    public JibriService(JitsiConfig jitsiConfig, RestTemplate restTemplate) {
        this.jitsiConfig = jitsiConfig;
        this.restTemplate = restTemplate;
    }

    public void startRecording(String roomName) {
        String url = jitsiConfig.getJibriApiUrl() + "/start";

        Map<String, String> request = new HashMap<>();
        request.put("room", roomName);
        request.put("appId", jitsiConfig.getAppId());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(request, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        if (response.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Failed to start recording: " + response.getBody());
        }
    }

    public void stopRecording(String roomName) {
        String url = jitsiConfig.getJibriApiUrl() + "/stop";

        Map<String, String> request = new HashMap<>();
        request.put("room", roomName);
        request.put("appId", jitsiConfig.getAppId());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(request, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        if (response.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Failed to stop recording: " + response.getBody());
        }
    }
}*/