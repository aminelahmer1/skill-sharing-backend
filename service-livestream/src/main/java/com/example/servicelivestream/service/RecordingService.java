package com.example.servicelivestream.service;

import com.example.servicelivestream.dto.RecordingRequest;
import com.example.servicelivestream.dto.RecordingResponse;
import com.example.servicelivestream.entity.LivestreamSession;
import com.example.servicelivestream.entity.Recording;
import com.example.servicelivestream.feignclient.SkillServiceClient;
import com.example.servicelivestream.repository.LivestreamSessionRepository;
import com.example.servicelivestream.repository.RecordingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingService {

    private final LivestreamSessionRepository sessionRepository;
    private final RecordingRepository recordingRepository;
    private final LiveKitService liveKitService;
    private final SkillServiceClient skillServiceClient;

    @Value("${application.recording.directory:./recordings}")
    private String recordingDirectory;

    @Value("${application.recording.retention-days:30}")
    private int retentionDays;

    @Transactional
    public RecordingResponse startRecording(Long sessionId, RecordingRequest request, String token) {
        log.info("Starting recording for session: {}", sessionId);

        LivestreamSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Session not found"));

        // Vérifier si un enregistrement est déjà en cours
        boolean hasActiveRecording = recordingRepository.findBySessionIdAndStatus(sessionId, "RECORDING")
                .isPresent();

        if (hasActiveRecording) {
            throw new ResponseStatusException(CONFLICT, "Un enregistrement est déjà en cours pour cette session");
        }

        // Récupérer le nom de la compétence
        String skillName = getSkillName(session.getSkillId(), token);

        // Compter les enregistrements existants pour cette session
        long recordingCount = recordingRepository.countBySessionId(sessionId);

        // Générer le nom du fichier avec le nom de la compétence et le numéro
        String fileName = generateRecordingFileName(skillName, recordingCount + 1, request.format());

        // Créer le chemin complet avec sous-dossier par session
        Path sessionDir = Paths.get(recordingDirectory, "session_" + sessionId);
        Path recordingPath = sessionDir.resolve(fileName);

        try {
            // Créer les répertoires nécessaires
            Files.createDirectories(sessionDir);
            log.info("Created directory: {}", sessionDir);

            // Vérifier l'espace disque
            checkDiskSpace(sessionDir);

            // Créer l'entité Recording avec statut temporaire
            Recording recording = Recording.builder()
                    .session(session)
                    .filePath(recordingPath.toString())
                    .fileName(fileName)
                    .skillName(skillName)
                    .recordingNumber((int) (recordingCount + 1))
                    .status("RECORDING")
                    .startedAt(LocalDateTime.now())
                    .authorizedUsers(getAllAuthorizedUsers(session))
                    .build();

            Recording savedRecording = recordingRepository.save(recording);

            // Démarrer l'enregistrement LiveKit
            liveKitService.startRoomRecording(session.getRoomName(), recordingPath.toString());

            log.info("Recording started: {} at path {}", fileName, recordingPath);

            return RecordingResponse.builder()
                    .recordingId(savedRecording.getId().toString())
                    .sessionId(sessionId)
                    .fileName(fileName)
                    .skillName(skillName)
                    .recordingNumber((int) (recordingCount + 1))
                    .status("RECORDING")
                    .startTime(savedRecording.getStartedAt())
                    .build();

        } catch (IOException e) {
            log.error("Failed to create recording directory", e);
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Échec de création du répertoire");
        }
    }

    @Transactional
    public void stopRecording(Long sessionId, String token) {
        log.info("Stopping recording for session: {}", sessionId);

        LivestreamSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Session not found"));

        // Récupérer l'enregistrement en cours
        Recording recording = recordingRepository.findBySessionIdAndStatus(sessionId, "RECORDING")
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Aucun enregistrement en cours"));

        try {
            // Arrêter l'enregistrement LiveKit
            liveKitService.stopRoomRecording(session.getRoomName());

            // Attendre un peu pour que le fichier soit écrit
            Thread.sleep(500);

            // Mettre à jour le statut et les métadonnées
            recording.setStatus("COMPLETED");
            recording.setEndedAt(LocalDateTime.now());
            recording.setDuration(calculateDuration(recording.getStartedAt(), recording.getEndedAt()));

            // Vérifier que le fichier existe et obtenir sa taille
            Path filePath = Paths.get(recording.getFilePath());
            if (Files.exists(filePath)) {
                long fileSize = Files.size(filePath);
                recording.setFileSize(fileSize);
                log.info("Recording file size: {} bytes", fileSize);
            } else {
                log.warn("Recording file not found immediately at: {}", filePath);
                // Attendre encore un peu
                Thread.sleep(1000);
                if (Files.exists(filePath)) {
                    recording.setFileSize(Files.size(filePath));
                }
            }

            recordingRepository.save(recording);

            log.info("Recording stopped successfully: {}", recording.getFileName());

        } catch (Exception e) {
            log.error("Error handling recording file", e);
            // Sauvegarder quand même l'enregistrement avec le statut COMPLETED
            recording.setStatus("COMPLETED");
            recording.setEndedAt(LocalDateTime.now());
            recording.setDuration(calculateDuration(recording.getStartedAt(), recording.getEndedAt()));
            recordingRepository.save(recording);
        }
    }

    @Transactional(readOnly = true)
    public List<RecordingResponse> getSessionRecordings(Long sessionId, String token) {
        List<Recording> recordings = recordingRepository.findBySessionIdOrderByRecordingNumberAsc(sessionId);

        return recordings.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RecordingResponse> getUserRecordings(Long userId, String token) {
        // Récupérer tous les enregistrements où l'utilisateur est autorisé
        List<Recording> recordings = recordingRepository.findByAuthorizedUsersContaining(userId);

        log.info("Found {} recordings for user {}", recordings.size(), userId);

        return recordings.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RecordingResponse getRecordingById(Long recordingId, String token) {
        Recording recording = recordingRepository.findById(recordingId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Enregistrement non trouvé"));

        return mapToResponse(recording);
    }

    @Transactional(readOnly = true)
    public String getRecordingFilePath(Long recordingId) {
        Recording recording = recordingRepository.findById(recordingId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Enregistrement non trouvé"));
        return recording.getFilePath();
    }

    @Transactional
    public void deleteRecording(Long recordingId, String token) {
        Recording recording = recordingRepository.findById(recordingId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Enregistrement non trouvé"));

        try {
            // Supprimer le fichier physique
            Path filePath = Paths.get(recording.getFilePath());
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("Deleted recording file: {}", filePath);
            }

            // Supprimer l'entrée en base
            recordingRepository.delete(recording);

            log.info("Recording deleted successfully: {}", recording.getFileName());

        } catch (IOException e) {
            log.error("Failed to delete recording file", e);
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Échec de suppression");
        }
    }

    // Méthodes privées helper

    private String getSkillName(Integer skillId, String token) {
        try {
            var skill = skillServiceClient.getSkillById(skillId);
            return skill != null ? skill.name() : "Unknown";
        } catch (Exception e) {
            log.error("Failed to fetch skill name", e);
            return "Recording";
        }
    }

    private String generateRecordingFileName(String skillName, long recordingNumber, String format) {
        // Nettoyer le nom de la compétence (remplacer caractères spéciaux)
        String cleanSkillName = skillName.replaceAll("[^a-zA-Z0-9]", "_");
        String extension = format != null && !format.isEmpty() ? format : "mp4";
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        // Format: SkillName_1_20240101_143022.mp4
        return String.format("%s_%d_%s.%s", cleanSkillName, recordingNumber, timestamp, extension);
    }

    private List<Long> getAllAuthorizedUsers(LivestreamSession session) {
        List<Long> authorizedUsers = new ArrayList<>();

        // Ajouter le producteur
        if (session.getProducerId() != null) {
            authorizedUsers.add(session.getProducerId());
        }

        // Ajouter les receveurs
        if (session.getReceiverIds() != null) {
            authorizedUsers.addAll(session.getReceiverIds());
        }

        return authorizedUsers.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private void checkDiskSpace(Path path) throws IOException {
        long usableSpace = Files.getFileStore(path).getUsableSpace();
        long requiredSpace = 1024L * 1024L * 1024L; // 1GB minimum

        if (usableSpace < requiredSpace) {
            throw new IOException("Espace disque insuffisant");
        }
    }

    private long calculateDuration(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) return 0;
        return java.time.Duration.between(start, end).getSeconds();
    }

    private RecordingResponse mapToResponse(Recording recording) {
        return RecordingResponse.builder()
                .recordingId(recording.getId().toString())
                .sessionId(recording.getSession().getId())
                .fileName(recording.getFileName())
                .skillName(recording.getSkillName())
                .recordingNumber(recording.getRecordingNumber())
                .status(recording.getStatus())
                .startTime(recording.getStartedAt())
                .endTime(recording.getEndedAt())
                .duration(recording.getDuration())
                .fileSize(recording.getFileSize())
                .downloadUrl("/api/v1/livestream/recordings/download/" + recording.getId())
                .build();
    }

    // Méthode de nettoyage périodique
    @Transactional
    public void cleanupOldRecordings() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
        List<Recording> oldRecordings = recordingRepository.findByCreatedAtBefore(cutoffDate);

        for (Recording recording : oldRecordings) {
            try {
                Path filePath = Paths.get(recording.getFilePath());
                if (Files.exists(filePath)) {
                    Files.delete(filePath);
                }
                recordingRepository.delete(recording);
            } catch (IOException e) {
                log.error("Failed to delete old recording: {}", recording.getFilePath(), e);
            }
        }

        log.info("Cleaned up {} old recordings", oldRecordings.size());
    }
}