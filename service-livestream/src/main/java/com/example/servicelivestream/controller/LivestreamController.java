package com.example.servicelivestream.controller;

import com.example.servicelivestream.dto.ChatMessage;
import com.example.servicelivestream.dto.RecordingRequest;
import com.example.servicelivestream.dto.RecordingResponse;
import com.example.servicelivestream.entity.LivestreamSession;
import com.example.servicelivestream.service.ChatService;
import com.example.servicelivestream.service.LivestreamService;
import com.example.servicelivestream.service.RecordingService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.apache.kafka.common.requests.DeleteAclsResponse.log;

@RestController
@RequestMapping("/api/v1/livestream")
@RequiredArgsConstructor
public class LivestreamController {
    private final LivestreamService livestreamService;
    private final ChatService chatService;
    private final RecordingService recordingService;

    @PostMapping("/start/{skillId}")
    public ResponseEntity<LivestreamSession> startSession(
            @PathVariable Integer skillId,
            @RequestParam(defaultValue = "true") boolean immediate,
            @AuthenticationPrincipal Jwt jwt) {
        LivestreamSession session = livestreamService.startSession(skillId, jwt, immediate);
        return ResponseEntity.ok(session);
    }


    @PostMapping("/end/{sessionId}")
    public ResponseEntity<Void> endSession(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        livestreamService.endSession(sessionId, jwt);
        return ResponseEntity.ok().build();
    }


    @GetMapping("/details/{sessionId}")
    public ResponseEntity<LivestreamSession> getSessionDetails(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        LivestreamSession session = livestreamService.getSession(sessionId, jwt);
        return ResponseEntity.ok(session);
    }
    // Dans LivestreamController
    @GetMapping("/{sessionId}/join")
    public ResponseEntity<String> joinSession(
            @PathVariable Long sessionId,
            @RequestParam(required = false, defaultValue = "false") boolean isProducer,
            @AuthenticationPrincipal Jwt jwt) {

        String joinToken = livestreamService.getJoinToken(sessionId, jwt, isProducer);
        return ResponseEntity.ok(joinToken);
    }
    @PreAuthorize("hasRole('RECEIVER') or hasRole('PRODUCER')")
    @GetMapping("/{sessionId}/messages")
    public ResponseEntity<List<ChatMessage>> getSessionMessages(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        List<ChatMessage> messages = chatService.getMessagesForSession(sessionId, jwt);
        return ResponseEntity.ok(messages);
    }


    @GetMapping("/recordings/{sessionId}")
    public ResponseEntity<Resource> getRecording(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal Jwt jwt) {
        try {
            LivestreamSession session = livestreamService.getSession(sessionId, jwt);
            File file = new File(session.getRecordingPath());

            if (!file.exists()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recording not found");
            }

            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("video/mp4"))
                    .body(resource);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to retrieve recording: " + e.getMessage(), e);
        }
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<?> getSession(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal Jwt jwt) {
        try {
            LivestreamSession session = livestreamService.getSession(sessionId, jwt);
            return ResponseEntity.ok(session);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Session fetch failed",
                            "message", e.getMessage(),
                            "timestamp", LocalDateTime.now()
                    ));
        }
    }
    @GetMapping("/skill/{skillId}")
    public ResponseEntity<?> getSessionBySkillId(
            @PathVariable Integer skillId,
            @AuthenticationPrincipal Jwt jwt) {

        LivestreamSession session = livestreamService.getSessionBySkillId(skillId, jwt);

        if (session == null) {
            log.info("Aucune session trouvée pour la compétence: {}", skillId);
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(session);
    }




}