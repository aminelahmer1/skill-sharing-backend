package com.example.servicelivestream.controller;

import com.example.servicelivestream.dto.ChatMessage;
import com.example.servicelivestream.entity.LivestreamSession;
import com.example.servicelivestream.service.ChatService;
import com.example.servicelivestream.service.LivestreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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


    @PostMapping("/start/{skillId}")
    public ResponseEntity<LivestreamSession> startSession(
            @PathVariable Integer skillId,
            @RequestParam(defaultValue = "true") boolean immediate,
            @AuthenticationPrincipal Jwt jwt
    ) {
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
    @GetMapping("/{sessionId}/join")
    public ResponseEntity<String> joinSession(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String joinToken = livestreamService.getJoinToken(sessionId, jwt);
        return ResponseEntity.ok(joinToken);
    }

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
            @AuthenticationPrincipal Jwt jwt
    ) {
        try {
            LivestreamSession session = livestreamService.getSession(sessionId, jwt);
            String recordingPath = session.getRecordingPath();
            if (recordingPath == null) {
                log.warn("No recording path found for session ID: {}", sessionId);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recording not found for session ID: " + sessionId);
            }

            File file = new File(recordingPath);
            if (!file.exists() || !file.isFile()) {
                log.warn("Recording file does not exist or is not a valid file at path: {} for session ID: {}", recordingPath, sessionId);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recording file not found for session ID: " + sessionId);
            }

            Resource resource = new FileSystemResource(file);
            log.info("Serving recording for session ID: {} from path: {}", sessionId, recordingPath);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("video/mp4"))
                    .body(resource);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error retrieving recording for session ID: {}: {}", sessionId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve recording: " + e.getMessage(), e);
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
            log.error("Error fetching session {}: {}", sessionId, e.getMessage());
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
            return ResponseEntity.noContent().build(); // HTTP 204 - No Content
        }

        return ResponseEntity.ok(session);
    }


}