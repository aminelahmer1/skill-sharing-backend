package com.example.servicelivestream.controller;

import com.example.servicelivestream.dto.RecordingRequest;
import com.example.servicelivestream.dto.RecordingResponse;
import com.example.servicelivestream.entity.Recording;
import com.example.servicelivestream.repository.RecordingRepository;
import com.example.servicelivestream.service.RecordingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Slf4j
@RestController
@RequestMapping("/api/v1/livestream")
@RequiredArgsConstructor
public class RecordingController {
    @Value("${application.recording.directory:./recordings}")
    private String recordingDirectory;
    private final RecordingService recordingService;
    private  final RecordingRepository recordingRepository;


    @PostMapping("/upload-recording")
    public ResponseEntity<Map<String, String>> uploadRecording(
            @RequestParam("recording") MultipartFile file,
            @RequestParam("sessionId") Long sessionId,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Receiving client recording upload: session={}, size={}", sessionId, file.getSize());

        try {
            // Validation des paramètres
            if (file.isEmpty()) {
                log.error("Empty file received");
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Empty file received", "success", "false"));
            }

            if (sessionId == null || sessionId <= 0) {
                log.error("Invalid session ID: {}", sessionId);
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Invalid session ID", "success", "false"));
            }

            // CORRECTION: Utiliser List au lieu d'Optional
            List<Recording> activeRecordings = recordingRepository.findBySessionIdAndStatus(sessionId, "RECORDING");

            if (activeRecordings.isEmpty()) {
                log.error("No active recording found for session: {}", sessionId);
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "No active recording found", "success", "false"));
            }

            Recording recording = activeRecordings.get(0); // Prendre le premier enregistrement actif

            // Créer le chemin de fichier
            String fileName = file.getOriginalFilename();
            if (fileName == null || fileName.isEmpty()) {
                fileName = "recording_" + sessionId + "_" + System.currentTimeMillis() + ".webm";
            }

            // Validation du type de fichier
            String contentType = file.getContentType();
            if (contentType == null || (!contentType.contains("webm") && !contentType.contains("video"))) {
                log.warn("Unexpected content type: {}, proceeding anyway", contentType);
            }

            Path uploadPath = Paths.get(recordingDirectory, "session_" + sessionId, fileName);

            // Créer les répertoires
            try {
                Files.createDirectories(uploadPath.getParent());
            } catch (IOException e) {
                log.error("Failed to create directories", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Failed to create upload directory", "success", "false"));
            }

            // Sauvegarder le fichier
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, uploadPath, StandardCopyOption.REPLACE_EXISTING);
                log.info("File saved successfully: {}", uploadPath);
            } catch (IOException e) {
                log.error("Failed to save file", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Failed to save file", "success", "false"));
            }

            // Mettre à jour l'enregistrement
            recording.setFilePath(uploadPath.toString());
            recording.setFileName(fileName);
            recording.setFileSize(file.getSize());
            recording.setStatus("COMPLETED");
            recording.setEndedAt(LocalDateTime.now());
            recording.setDuration(calculateDuration(recording.getStartedAt(), recording.getEndedAt()));

            try {
                recordingRepository.save(recording);
                log.info("Recording updated successfully in database");
            } catch (Exception e) {
                log.error("Failed to update recording in database", e);
                // Essayer de supprimer le fichier qui a été sauvé
                try {
                    Files.deleteIfExists(uploadPath);
                } catch (IOException deleteError) {
                    log.error("Failed to cleanup file after database error", deleteError);
                }
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Database update failed", "success", "false"));
            }

            log.info("Client recording processed successfully: {} ({} bytes)", fileName, file.getSize());

            Map<String, String> response = Map.of(
                    "message", "Upload successful",
                    "success", "true",
                    "fileName", fileName,
                    "recordingId", recording.getId().toString()
            );

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response);

        } catch (Exception e) {
            log.error("Unexpected error during recording upload", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Upload failed: " + e.getMessage(),
                            "success", "false"
                    ));
        }
    }

    // Méthode helper pour calculer la durée
    private long calculateDuration(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return 0;
        }
        try {
            return java.time.Duration.between(start, end).getSeconds();
        } catch (Exception e) {
            log.error("Error calculating duration", e);
            return 0;
        }
    }
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
    }



    @PostMapping("/{sessionId}/recording/cleanup")
    public ResponseEntity<Void> cleanupOrphanRecordings(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal Jwt jwt) {
        log.info("Manual cleanup requested for session {}", sessionId);
        recordingService.cleanupOrphanRecordings(sessionId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{sessionId}/recording/emergency-stop")
    public ResponseEntity<Void> emergencyStopRecording(
            @PathVariable Long sessionId,
            @RequestBody(required = false) Map<String, Object> body) {
        log.warn("EMERGENCY STOP requested for session {}", sessionId);

        try {
            List<Recording> activeRecordings = recordingRepository
                    .findBySessionIdAndStatus(sessionId, "RECORDING");

            if (!activeRecordings.isEmpty()) {
                Recording recording = activeRecordings.get(0);
                recording.setStatus("COMPLETED");
                recording.setEndedAt(LocalDateTime.now());
                recording.setDuration(calculateDuration(recording.getStartedAt(), recording.getEndedAt()));
                recordingRepository.save(recording);
                log.info("Emergency stop completed for recording {}", recording.getId());
            }
        } catch (Exception e) {
            log.error("Emergency stop failed", e);
        }

        return ResponseEntity.ok().build();
    }




    @GetMapping("/{sessionId}/recording/status")
    public ResponseEntity<RecordingResponse> getRecordingStatus(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal Jwt jwt) {
        List<Recording> activeRecordings = recordingRepository
                .findBySessionIdAndStatus(sessionId, "RECORDING");

        if (!activeRecordings.isEmpty()) {
            // Utiliser le service pour obtenir la réponse
            String token = "Bearer " + jwt.getTokenValue();
            RecordingResponse response = recordingService.getRecordingById(activeRecordings.get(0).getId(), token);
            return ResponseEntity.ok(response);
        }

        return ResponseEntity.noContent().build();
    }



}