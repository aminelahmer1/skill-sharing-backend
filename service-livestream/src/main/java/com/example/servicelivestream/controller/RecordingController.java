package com.example.servicelivestream.controller;

import com.example.servicelivestream.dto.RecordingRequest;
import com.example.servicelivestream.dto.RecordingResponse;
import com.example.servicelivestream.dto.UserResponse;
import com.example.servicelivestream.entity.Recording;
import com.example.servicelivestream.feignclient.UserServiceClient;
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
import java.util.*;

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
private  final UserServiceClient userServiceClient;

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
        // Récupérer le Keycloak ID (UUID) du JWT
        String keycloakId = jwt.getSubject(); // Ceci retourne "bc214ec2-8ddb-4329-95dd-5fed5ecfe5a4"

        if (keycloakId == null || keycloakId.isEmpty()) {
            throw new IllegalStateException("No subject found in JWT");
        }

        try {
            // Appeler le service utilisateur pour obtenir l'ID réel
            String token = "Bearer " + jwt.getTokenValue();
            UserResponse user = userServiceClient.getUserByKeycloakId(keycloakId, token);

            if (user != null && user.id() != null) {
                log.info("Resolved user ID {} for Keycloak ID {}", user.id(), keycloakId);
                return user.id();
            }

            throw new IllegalStateException("User not found for Keycloak ID: " + keycloakId);

        } catch (Exception e) {
            log.error("Failed to get user ID for Keycloak ID {}: {}", keycloakId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to resolve user ID");
        }
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


// Ajouter ces méthodes au RecordingController existant après les méthodes existantes

    @GetMapping("/recordings/producer/{producerId}")
    public ResponseEntity<Map<String, List<RecordingResponse>>> getProducerRecordings(
            @PathVariable Long producerId,
            @AuthenticationPrincipal Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        Long requestingUserId = getUserIdFromJwt(jwt);

        // Vérifier que l'utilisateur demande ses propres enregistrements
        if (!requestingUserId.equals(producerId)) {
            log.warn("User {} trying to access producer {} recordings", requestingUserId, producerId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Map<String, List<RecordingResponse>> groupedRecordings =
                recordingService.getProducerRecordingsGroupedBySkill(producerId, token);
        return ResponseEntity.ok(groupedRecordings);
    }

    @GetMapping("/recordings/receiver/{receiverId}")
    public ResponseEntity<Map<String, List<RecordingResponse>>> getReceiverRecordings(
            @PathVariable Long receiverId,
            @AuthenticationPrincipal Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        Long requestingUserId = getUserIdFromJwt(jwt);

        // Vérifier que l'utilisateur demande ses propres enregistrements
        if (!requestingUserId.equals(receiverId)) {
            log.warn("User {} trying to access receiver {} recordings", requestingUserId, receiverId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Map<String, List<RecordingResponse>> groupedRecordings =
                recordingService.getReceiverRecordingsGroupedBySkill(receiverId, token);
        return ResponseEntity.ok(groupedRecordings);
    }

    @GetMapping("/recordings/skill/{skillId}")
    public ResponseEntity<List<RecordingResponse>> getSkillRecordings(
            @PathVariable Integer skillId,
            @AuthenticationPrincipal Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        Long userId = getUserIdFromJwt(jwt);

        log.info("User {} requesting recordings for skill {}", userId, skillId);

        // Le service vérifiera l'autorisation
        List<RecordingResponse> recordings =
                recordingService.getSkillRecordingsForUser(skillId, userId, token);

        if (recordings.isEmpty()) {
            log.info("No recordings found for skill {} and user {}", skillId, userId);
        }

        return ResponseEntity.ok(recordings);
    }

    @GetMapping("/recordings/finished-skills")
    public ResponseEntity<Map<Integer, List<RecordingResponse>>> getFinishedSkillsRecordings(
            @AuthenticationPrincipal Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        Long userId = getUserIdFromJwt(jwt);

        log.info("User {} requesting finished skills recordings", userId);

        Map<Integer, List<RecordingResponse>> recordings =
                recordingService.getFinishedSkillsRecordings(userId, token);

        return ResponseEntity.ok(recordings);
    }

    @GetMapping("/recordings/producer")
    public ResponseEntity<Map<String, List<RecordingResponse>>> getCurrentProducerRecordings(
            @AuthenticationPrincipal Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        Long userId = getUserIdFromJwt(jwt);

        log.info("Current producer {} requesting their recordings", userId);

        Map<String, List<RecordingResponse>> groupedRecordings =
                recordingService.getProducerRecordingsGroupedBySkill(userId, token);
        return ResponseEntity.ok(groupedRecordings);
    }

    @GetMapping("/recordings/receiver")
    public ResponseEntity<Map<String, List<RecordingResponse>>> getCurrentReceiverRecordings(
            @AuthenticationPrincipal Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        Long userId = getUserIdFromJwt(jwt);

        log.info("Current receiver {} requesting their recordings", userId);

        Map<String, List<RecordingResponse>> groupedRecordings =
                recordingService.getReceiverRecordingsGroupedBySkill(userId, token);
        return ResponseEntity.ok(groupedRecordings);
    }

    @GetMapping("/recordings/stats")
    public ResponseEntity<Map<String, Object>> getRecordingStats(
            @AuthenticationPrincipal Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        Long userId = getUserIdFromJwt(jwt);

        Map<String, Object> stats = new HashMap<>();

        try {
            // Déterminer si l'utilisateur est producteur ou receiver
            boolean isProducer = isUserProducer(jwt);

            Map<String, List<RecordingResponse>> recordings;
            if (isProducer) {
                recordings = recordingService.getProducerRecordingsGroupedBySkill(userId, token);
            } else {
                recordings = recordingService.getReceiverRecordingsGroupedBySkill(userId, token);
            }

            // Calculer les statistiques
            int totalRecordings = 0;
            long totalDuration = 0;
            long totalSize = 0;
            Map<String, Integer> recordingsBySkill = new HashMap<>();

            for (Map.Entry<String, List<RecordingResponse>> entry : recordings.entrySet()) {
                String skillKey = entry.getKey();
                List<RecordingResponse> recs = entry.getValue();

                totalRecordings += recs.size();
                recordingsBySkill.put(skillKey, recs.size());

                for (RecordingResponse rec : recs) {
                    if (rec.getDuration() != null) {
                        totalDuration += rec.getDuration();
                    }
                    if (rec.getFileSize() != null) {
                        totalSize += rec.getFileSize();
                    }
                }
            }

            stats.put("totalRecordings", totalRecordings);
            stats.put("totalDuration", totalDuration);
            stats.put("totalSize", totalSize);
            stats.put("recordingsBySkill", recordingsBySkill);
            stats.put("userType", isProducer ? "PRODUCER" : "RECEIVER");

        } catch (Exception e) {
            log.error("Error calculating recording stats for user {}: {}", userId, e.getMessage());
            stats.put("error", "Failed to calculate statistics");
        }

        return ResponseEntity.ok(stats);
    }

    // Méthode helper pour vérifier si l'utilisateur est producteur
    private boolean isUserProducer(Jwt jwt) {
        // Vérifier les rôles dans le JWT
        List<String> roles = new ArrayList<>();

        // Essayer realm_access.roles
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && realmAccess.containsKey("roles")) {
            Object rolesObj = realmAccess.get("roles");
            if (rolesObj instanceof List) {
                roles.addAll((List<String>) rolesObj);
            }
        }

        // Essayer resource_access.backend-service.roles
        Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess != null && resourceAccess.containsKey("backend-service")) {
            Map<String, Object> backendService = (Map<String, Object>) resourceAccess.get("backend-service");
            if (backendService != null && backendService.containsKey("roles")) {
                Object rolesObj = backendService.get("roles");
                if (rolesObj instanceof List) {
                    roles.addAll((List<String>) rolesObj);
                }
            }
        }

        return roles.contains("PRODUCER");
    }
}