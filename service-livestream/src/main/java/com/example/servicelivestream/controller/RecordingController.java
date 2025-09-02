package com.example.servicelivestream.controller;

import com.example.servicelivestream.dto.RecordingRequest;
import com.example.servicelivestream.dto.RecordingResponse;
import com.example.servicelivestream.service.RecordingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/livestream")
@RequiredArgsConstructor
public class RecordingController {

    private final RecordingService recordingService;

    @PostMapping("/{sessionId}/recording/start")
    public ResponseEntity<RecordingResponse> startRecording(
            @PathVariable Long sessionId,
            @RequestBody RecordingRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        RecordingResponse response = recordingService.startRecording(sessionId, request, token);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{sessionId}/recording/stop")
    public ResponseEntity<Void> stopRecording(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        recordingService.stopRecording(sessionId, token);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{sessionId}/recordings")
    public ResponseEntity<List<RecordingResponse>> getSessionRecordings(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        List<RecordingResponse> recordings = recordingService.getSessionRecordings(sessionId, token);
        return ResponseEntity.ok(recordings);
    }

    @GetMapping("/recordings/{recordingId}")
    public ResponseEntity<RecordingResponse> getRecordingById(
            @PathVariable Long recordingId,
            @AuthenticationPrincipal Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        RecordingResponse recording = recordingService.getRecordingById(recordingId, token);
        return ResponseEntity.ok(recording);
    }

    @GetMapping("/recordings/user")
    public ResponseEntity<List<RecordingResponse>> getUserRecordings(
            @AuthenticationPrincipal Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        Long userId = getUserIdFromJwt(jwt);
        List<RecordingResponse> recordings = recordingService.getUserRecordings(userId, token);
        return ResponseEntity.ok(recordings);
    }

    @GetMapping("/recordings/download/{recordingId}")
    public ResponseEntity<Resource> downloadRecording(
            @PathVariable Long recordingId,
            @AuthenticationPrincipal Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        RecordingResponse recording = recordingService.getRecordingById(recordingId, token);

        // CORRECTION: Utiliser le chemin complet du fichier, pas juste le nom
        Path filePath = Paths.get(recordingService.getRecordingFilePath(recordingId));
        File file = filePath.toFile();

        if (!file.exists()) {
            log.error("File not found: {}", filePath);
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);

        // Déterminer le type MIME basé sur l'extension
        String contentType = "video/mp4";
        if (recording.getFileName().endsWith(".webm")) {
            contentType = "video/webm";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + recording.getFileName() + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(file.length()))
                .body(resource);
    }

    @DeleteMapping("/recordings/{recordingId}")
    public ResponseEntity<Void> deleteRecording(
            @PathVariable Long recordingId,
            @AuthenticationPrincipal Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        recordingService.deleteRecording(recordingId, token);
        return ResponseEntity.noContent().build();
    }

    private Long getUserIdFromJwt(Jwt jwt) {
        // Essayer d'abord avec 'sub' qui devrait contenir l'ID utilisateur
        Object userIdClaim = jwt.getClaim("sub");

        if (userIdClaim != null) {
            try {
                // Si c'est un UUID, le convertir en long via hash
                String userIdStr = userIdClaim.toString();
                if (userIdStr.contains("-")) { // C'est un UUID
                    return (long) userIdStr.hashCode() & 0xFFFFFFFFL;
                } else {
                    return Long.parseLong(userIdStr);
                }
            } catch (NumberFormatException e) {
                log.warn("Failed to parse user ID from JWT: {}, using hash fallback", userIdClaim);
                return (long) userIdClaim.toString().hashCode() & 0xFFFFFFFFL;
            }
        }

        // Fallback - essayer avec d'autres claims
        String username = jwt.getClaim("preferred_username");
        if (username != null) {
            return (long) username.hashCode() & 0xFFFFFFFFL;
        }

        String email = jwt.getClaim("email");
        if (email != null) {
            return (long) email.hashCode() & 0xFFFFFFFFL;
        }

        throw new IllegalStateException("Cannot extract user ID from JWT");
    }}