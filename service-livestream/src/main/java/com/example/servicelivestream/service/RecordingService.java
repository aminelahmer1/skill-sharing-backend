package com.example.servicelivestream.service;

import com.example.servicelivestream.dto.RecordingRequest;
import com.example.servicelivestream.dto.RecordingResponse;
import com.example.servicelivestream.dto.UserResponse;
import com.example.servicelivestream.entity.LivestreamSession;
import com.example.servicelivestream.entity.Recording;
import com.example.servicelivestream.feignclient.UserServiceClient;
import com.example.servicelivestream.repository.LivestreamSessionRepository;
import com.example.servicelivestream.repository.RecordingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingService {

    private final LivestreamSessionRepository sessionRepository;
    private final RecordingRepository recordingRepository;
    private final LiveKitService liveKitService;
    private final UserServiceClient userServiceClient;

    @Value("${application.recording.directory:./recordings}")
    private String recordingDirectory;

    @Value("${application.recording.max-size:1GB}")
    private String maxRecordingSize;

    @Value("${application.recording.retention-days:30}")
    private int retentionDays;

    @Transactional
    public RecordingResponse startRecording(Long sessionId, RecordingRequest request, Jwt jwt) {
        log.info("Starting recording for session: {}", sessionId);

        LivestreamSession session = validateSessionForRecording(sessionId, jwt);

        // Vérifier qu'il n'y a pas déjà un enregistrement en cours
        if (session.getRecordingPath() != null) {
            // Vérifier si l'enregistrement est toujours actif
            Recording existingRecording = recordingRepository.findBySession(session).orElse(null);
            if (existingRecording == null) {
                // L'enregistrement précédent a peut-être échoué, on peut réessayer
                log.warn("Previous recording path exists but no recording entity found. Proceeding with new recording.");
            } else {
                throw new ResponseStatusException(CONFLICT, "Recording already exists for this session");
            }
        }

        String recordingId = UUID.randomUUID().toString();
        String fileName = generateFileName(sessionId, recordingId, request.format());
        Path recordingPath = Paths.get(recordingDirectory, fileName);

        try {
            // Créer le répertoire s'il n'existe pas
            Files.createDirectories(Paths.get(recordingDirectory));

            // Vérifier l'espace disque disponible
            checkDiskSpace(recordingPath);

            // Appeler l'API LiveKit pour démarrer l'enregistrement
            liveKitService.startRoomRecording(session.getRoomName(), recordingPath.toString());

            // Mettre à jour la session avec le chemin d'enregistrement
            session.setRecordingPath(recordingPath.toString());
            sessionRepository.save(session);

            log.info("Recording started successfully for session {} at path {}", sessionId, recordingPath);

            return new RecordingResponse(
                    recordingId,
                    sessionId,
                    "RECORDING",
                    LocalDateTime.now(),
                    null,
                    null
            );

        } catch (IOException e) {
            log.error("Failed to create recording directory", e);
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Failed to start recording: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error starting recording", e);
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Failed to start recording: " + e.getMessage());
        }
    }

    @Transactional
    public void stopRecording(Long sessionId, Jwt jwt) {
        log.info("Stopping recording for session: {}", sessionId);

        LivestreamSession session = validateSessionForRecording(sessionId, jwt);

        if (session.getRecordingPath() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "No recording in progress for this session");
        }

        try {
            // Vérifier si un enregistrement existe déjà
            Recording existingRecording = recordingRepository.findBySession(session).orElse(null);
            if (existingRecording != null) {
                log.warn("Recording already stopped for session {}", sessionId);
                return;
            }

            // Appeler l'API LiveKit pour arrêter l'enregistrement
            liveKitService.stopRoomRecording(session.getRoomName());

            // Créer l'entité Recording
            Recording recording = Recording.builder()
                    .session(session)
                    .filePath(session.getRecordingPath())
                    .createdAt(LocalDateTime.now())
                    .authorizedUsers(getAllAuthorizedUsers(session))
                    .build();

            recordingRepository.save(recording);

            // Vérifier que le fichier existe
            Path recordingPath = Paths.get(session.getRecordingPath());
            if (!Files.exists(recordingPath)) {
                log.warn("Recording file not found at path: {}", recordingPath);
            } else {
                long fileSize = Files.size(recordingPath);
                log.info("Recording stopped for session {}. File size: {} bytes", sessionId, fileSize);
            }

        } catch (IOException e) {
            log.error("Error checking recording file", e);
        } catch (Exception e) {
            log.error("Failed to stop recording", e);
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Failed to stop recording: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public RecordingResponse getRecordingStatus(Long sessionId, Jwt jwt) {
        LivestreamSession session = getSessionWithAuth(sessionId, jwt);

        if (session.getRecordingPath() == null) {
            return new RecordingResponse(
                    null,
                    sessionId,
                    "NOT_RECORDING",
                    null,
                    null,
                    null
            );
        }

        // Vérifier si un enregistrement existe
        Recording recording = recordingRepository.findBySession(session).orElse(null);

        if (recording != null) {
            // L'enregistrement est terminé
            return new RecordingResponse(
                    recording.getId().toString(),
                    sessionId,
                    "COMPLETED",
                    recording.getCreatedAt(),
                    recording.getCreatedAt(),
                    "/api/v1/livestream/recordings/" + sessionId
            );
        }

        // L'enregistrement est en cours
        return new RecordingResponse(
                UUID.randomUUID().toString(),
                sessionId,
                "RECORDING",
                session.getStartTime() != null ? session.getStartTime() : LocalDateTime.now(),
                null,
                null
        );
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "userRecordings", key = "#jwt.subject")
    public List<RecordingResponse> getUserRecordings(Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        UserResponse currentUser = userServiceClient.getUserByKeycloakId(jwt.getSubject(), token);

        List<Recording> recordings = recordingRepository.findByAuthorizedUsersContaining(currentUser.id());

        return recordings.stream()
                .map(this::mapToRecordingResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Recording getRecordingById(Long recordingId, Jwt jwt) {
        Recording recording = recordingRepository.findById(recordingId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Recording not found"));

        // Vérifier l'autorisation
        String token = "Bearer " + jwt.getTokenValue();
        UserResponse currentUser = userServiceClient.getUserByKeycloakId(jwt.getSubject(), token);

        if (!recording.getAuthorizedUsers().contains(currentUser.id()) &&
                !recording.getSession().getProducerId().equals(currentUser.id())) {
            throw new ResponseStatusException(FORBIDDEN, "Not authorized to access this recording");
        }

        return recording;
    }

    @Transactional
    public void deleteRecording(Long sessionId, Jwt jwt) {
        LivestreamSession session = validateSessionForRecording(sessionId, jwt);

        Recording recording = recordingRepository.findBySession(session)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "No recording found for this session"));

        try {
            // Supprimer le fichier physique
            Path recordingPath = Paths.get(recording.getFilePath());
            if (Files.exists(recordingPath)) {
                Files.delete(recordingPath);
                log.info("Deleted recording file: {}", recordingPath);
            }

            // Supprimer l'entrée en base de données
            recordingRepository.delete(recording);

            // Nettoyer la référence dans la session
            session.setRecordingPath(null);
            sessionRepository.save(session);

            log.info("Recording deleted successfully for session {}", sessionId);

        } catch (IOException e) {
            log.error("Failed to delete recording file", e);
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Failed to delete recording");
        }
    }

    // Méthodes privées utilitaires

    private LivestreamSession validateSessionForRecording(Long sessionId, Jwt jwt) {
        LivestreamSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Session not found"));

        // Récupérer l'utilisateur actuel
        String token = "Bearer " + jwt.getTokenValue();
        UserResponse currentUser = userServiceClient.getUserByKeycloakId(jwt.getSubject(), token);

        // Vérifier que l'utilisateur est le producteur
        if (!session.getProducerId().equals(currentUser.id())) {
            throw new ResponseStatusException(FORBIDDEN, "Only the producer can manage recordings");
        }

        // Vérifier que la session est en cours
        if (!"LIVE".equals(session.getStatus())) {
            throw new ResponseStatusException(BAD_REQUEST, "Session must be live to manage recordings");
        }

        return session;
    }

    private LivestreamSession getSessionWithAuth(Long sessionId, Jwt jwt) {
        LivestreamSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Session not found"));

        // Récupérer l'utilisateur actuel
        String token = "Bearer " + jwt.getTokenValue();
        UserResponse currentUser = userServiceClient.getUserByKeycloakId(jwt.getSubject(), token);

        // Vérifier que l'utilisateur est autorisé
        if (!session.getProducerId().equals(currentUser.id()) &&
                !session.getReceiverIds().contains(currentUser.id())) {
            throw new ResponseStatusException(FORBIDDEN, "Not authorized to access this session");
        }

        return session;
    }

    private List<Long> getAllAuthorizedUsers(LivestreamSession session) {
        List<Long> authorizedUsers = new java.util.ArrayList<>(session.getReceiverIds());
        authorizedUsers.add(session.getProducerId());
        return authorizedUsers;
    }

    private String generateFileName(Long sessionId, String recordingId, String format) {
        String extension = format != null && !format.isEmpty() ? format : "mp4";
        return String.format("session_%d_%s_%s.%s",
                sessionId,
                recordingId,
                LocalDateTime.now().toString().replaceAll("[^a-zA-Z0-9]", "_"),
                extension);
    }

    private void checkDiskSpace(Path path) throws IOException {
        long usableSpace = Files.getFileStore(path.getParent()).getUsableSpace();
        long requiredSpace = parseSize(maxRecordingSize);

        if (usableSpace < requiredSpace) {
            throw new IOException("Insufficient disk space for recording. Available: " +
                    formatBytes(usableSpace) + ", Required: " + formatBytes(requiredSpace));
        }
    }

    private long parseSize(String size) {
        // Simple parser pour convertir "1GB" en bytes
        size = size.toUpperCase().trim();
        long multiplier = 1;

        if (size.endsWith("GB")) {
            multiplier = 1024L * 1024L * 1024L;
            size = size.substring(0, size.length() - 2);
        } else if (size.endsWith("MB")) {
            multiplier = 1024L * 1024L;
            size = size.substring(0, size.length() - 2);
        } else if (size.endsWith("KB")) {
            multiplier = 1024L;
            size = size.substring(0, size.length() - 2);
        }

        try {
            return Long.parseLong(size.trim()) * multiplier;
        } catch (NumberFormatException e) {
            return 1024L * 1024L * 1024L; // Default 1GB
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)) + " MB";
        return (bytes / (1024 * 1024 * 1024)) + " GB";
    }

    private RecordingResponse mapToRecordingResponse(Recording recording) {
        return new RecordingResponse(
                recording.getId().toString(),
                recording.getSession().getId(),
                "COMPLETED",
                recording.getCreatedAt(),
                recording.getCreatedAt(),
                "/api/v1/livestream/recordings/" + recording.getSession().getId()
        );
    }

    // Méthode pour nettoyer les anciens enregistrements
    @Transactional
    public void cleanupOldRecordings() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
        List<Recording> oldRecordings = recordingRepository.findByCreatedAtBefore(cutoffDate);

        for (Recording recording : oldRecordings) {
            try {
                Path recordingPath = Paths.get(recording.getFilePath());
                if (Files.exists(recordingPath)) {
                    Files.delete(recordingPath);
                    log.info("Deleted old recording file: {}", recordingPath);
                }
                recordingRepository.delete(recording);
            } catch (IOException e) {
                log.error("Failed to delete old recording: {}", recording.getFilePath(), e);
            }
        }

        log.info("Cleaned up {} old recordings", oldRecordings.size());
    }
}