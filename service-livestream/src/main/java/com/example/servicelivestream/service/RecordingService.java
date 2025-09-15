package com.example.servicelivestream.service;

import com.example.serviceexchange.FeignClient.UserServiceClient;
import com.example.servicelivestream.dto.RecordingRequest;
import com.example.servicelivestream.dto.RecordingResponse;
import com.example.servicelivestream.dto.SkillResponse;
import com.example.servicelivestream.entity.LivestreamSession;
import com.example.servicelivestream.entity.Recording;
import com.example.servicelivestream.feignclient.SkillServiceClient;
import com.example.servicelivestream.repository.LivestreamSessionRepository;
import com.example.servicelivestream.repository.RecordingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
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

    @Value("${application.recording.default-duration-seconds:30}")
    private int defaultRecordingDuration;

    // ========== DÉMARRAGE ENREGISTREMENT ==========

    @Transactional
    public RecordingResponse startRecording(Long sessionId, RecordingRequest request, String token) {
        log.info("=== STARTING RECORDING SERVICE ===");
        log.info("Session ID: {}", sessionId);

        LivestreamSession session = findSession(sessionId);

        // NOUVEAU: Nettoyer les enregistrements orphelins avant de commencer
        cleanupOrphanRecordings(sessionId);

        validateNoActiveRecording(sessionId);

        String skillName = getSkillName(session.getSkillId(), token);
        long recordingCount = recordingRepository.countBySessionId(sessionId);
        String fileName = generateFileName(skillName, recordingCount + 1, request.format());

        Path recordingPath = createRecordingPath(sessionId, fileName);

        try {
            Files.createDirectories(recordingPath.getParent());
            checkDiskSpace(recordingPath.getParent());

            Recording recording = createRecordingEntity(session, recordingPath, fileName, skillName, recordingCount + 1);
            Recording savedRecording = recordingRepository.save(recording);

            liveKitService.startRoomRecording(session.getRoomName(), recordingPath.toString());

            log.info("=== RECORDING STARTED SUCCESSFULLY ===");
            log.info("File: {} at {}", fileName, recordingPath);

            return mapToResponse(savedRecording);

        } catch (IOException e) {
            log.error("Failed to create recording", e);
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Échec de création du répertoire");
        }
    }

    // ========== ARRÊT ENREGISTREMENT ==========

    @Transactional
    public void stopRecording(Long sessionId, String token) {
        log.info("=== STOPPING RECORDING SERVICE ===");
        log.info("Session ID: {}", sessionId);

        LivestreamSession session = findSession(sessionId);
        Recording recording = findActiveRecording(sessionId);

        try {
            // Arrêter l'enregistrement
            liveKitService.stopRoomRecording(session.getRoomName());

            // Mettre à jour l'enregistrement
            updateRecordingStatus(recording);

            log.info("=== RECORDING STOPPED SUCCESSFULLY ===");
            log.info("File: {}", recording.getFileName());

        } catch (Exception e) {
            log.error("Error stopping recording", e);
            markRecordingFailed(recording);
        }
    }
    @Transactional
    public void cleanupOrphanRecordings(Long sessionId) {
        // CHANGÉ: Maintenant utilise une List
        List<Recording> orphanRecordings = recordingRepository.findBySessionIdAndStatus(sessionId, "RECORDING");

        for (Recording orphan : orphanRecordings) {
            log.warn("Found orphan recording: {} for session {}", orphan.getId(), sessionId);

            Path filePath = Paths.get(orphan.getFilePath());
            if (Files.exists(filePath)) {
                try {
                    long fileSize = Files.size(filePath);
                    if (fileSize > 1024) {
                        orphan.setStatus("COMPLETED");
                        orphan.setEndedAt(LocalDateTime.now());
                        orphan.setFileSize(fileSize);
                        orphan.setDuration(calculateDuration(orphan.getStartedAt(), orphan.getEndedAt()));
                        log.info("Orphan recording {} marked as COMPLETED", orphan.getId());
                    } else {
                        orphan.setStatus("FAILED");
                        orphan.setEndedAt(LocalDateTime.now());
                        log.info("Orphan recording {} marked as FAILED (file too small)", orphan.getId());
                    }
                } catch (IOException e) {
                    orphan.setStatus("FAILED");
                    orphan.setEndedAt(LocalDateTime.now());
                    log.error("Error checking orphan recording file", e);
                }
            } else {
                orphan.setStatus("FAILED");
                orphan.setEndedAt(LocalDateTime.now());
                log.info("Orphan recording {} marked as FAILED (no file)", orphan.getId());
            }

            recordingRepository.save(orphan);
        }
    }

    // Méthode scheduled pour nettoyer périodiquement
    @Scheduled(fixedDelay = 300000) // Toutes les 5 minutes
    public void cleanupStaleRecordings() {
        LocalDateTime staleThreshold = LocalDateTime.now().minusMinutes(30);

        List<Recording> staleRecordings = recordingRepository
                .findByStatusAndStartedAtBefore("RECORDING", staleThreshold);

        for (Recording stale : staleRecordings) {
            log.info("Auto-cleanup stale recording: {}", stale.getId());
            cleanupOrphanRecordings(stale.getSession().getId());
        }
    }
    // ========== CONSULTATION ENREGISTREMENTS ==========

    @Transactional(readOnly = true)
    public List<RecordingResponse> getSessionRecordings(Long sessionId, String token) {
        List<Recording> recordings = recordingRepository.findBySessionIdOrderByRecordingNumberAsc(sessionId);
        log.info("Found {} recordings for session {}", recordings.size(), sessionId);
        return recordings.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RecordingResponse> getUserRecordings(Long userId, String token) {
        List<Recording> recordings = recordingRepository.findByAuthorizedUsersContaining(userId);
        log.info("Found {} recordings for user {}", recordings.size(), userId);
        return recordings.stream().map(this::mapToResponse).collect(Collectors.toList());
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

    // ========== SUPPRESSION ENREGISTREMENT ==========

    @Transactional
    public void deleteRecording(Long recordingId, String token) {
        Recording recording = recordingRepository.findById(recordingId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Enregistrement non trouvé"));

        try {
            // Supprimer le fichier physique
            Path filePath = Paths.get(recording.getFilePath());
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("Physical file deleted: {}", filePath);
            }

            // Supprimer de la base de données
            recordingRepository.delete(recording);
            log.info("Recording deleted from database: {}", recording.getFileName());

        } catch (IOException e) {
            log.error("Failed to delete recording file", e);
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Échec de suppression du fichier");
        }
    }

    // ========== NETTOYAGE AUTOMATIQUE ==========

    @Transactional
    public void cleanupOldRecordings() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
        List<Recording> oldRecordings = recordingRepository.findByCreatedAtBefore(cutoffDate);

        for (Recording recording : oldRecordings) {
            try {
                deleteRecording(recording.getId(), null);
            } catch (Exception e) {
                log.error("Failed to cleanup recording: {}", recording.getFilePath(), e);
            }
        }

        log.info("Cleaned up {} old recordings", oldRecordings.size());
    }

    // ========== MÉTHODES UTILITAIRES PRIVÉES ==========

    private LivestreamSession findSession(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Session non trouvée"));
    }

    private void validateNoActiveRecording(Long sessionId) {
        // CHANGÉ: Utilise la List et vérifie si elle n'est pas vide
        List<Recording> activeRecordings = recordingRepository.findBySessionIdAndStatus(sessionId, "RECORDING");
        if (!activeRecordings.isEmpty()) {
            throw new ResponseStatusException(CONFLICT, "Un enregistrement est déjà en cours");
        }
    }

    private Recording findActiveRecording(Long sessionId) {
        // CHANGÉ: Utilise la List et récupère le premier élément
        List<Recording> activeRecordings = recordingRepository.findBySessionIdAndStatus(sessionId, "RECORDING");
        if (activeRecordings.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Aucun enregistrement en cours");
        }
        return activeRecordings.get(0);
    }

    private String getSkillName(Integer skillId, String token) {
        try {
            var skill = skillServiceClient.getSkillById(skillId);
            return skill != null ? skill.name() : "Unknown_Skill";
        } catch (Exception e) {
            log.warn("Failed to fetch skill name for ID {}: {}", skillId, e.getMessage());
            return "Recording";
        }
    }

    private String generateFileName(String skillName, long recordingNumber, String format) {
        String cleanSkillName = skillName.replaceAll("[^a-zA-Z0-9]", "_");
        String extension = (format != null && !format.isEmpty()) ? format : "mp4";
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        return String.format("%s_%d_%s.%s", cleanSkillName, recordingNumber, timestamp, extension);
    }

    private Path createRecordingPath(Long sessionId, String fileName) {
        Path sessionDir = Paths.get(recordingDirectory, "session_" + sessionId);
        return sessionDir.resolve(fileName);
    }

    private Recording createRecordingEntity(LivestreamSession session, Path recordingPath,
                                            String fileName, String skillName, long recordingNumber) {
        return Recording.builder()
                .session(session)
                .filePath(recordingPath.toString())
                .fileName(fileName)
                .skillName(skillName)
                .recordingNumber((int) recordingNumber)
                .status("RECORDING")
                .startedAt(LocalDateTime.now())
                .authorizedUsers(getAuthorizedUsers(session))
                .build();
    }

    private List<Long> getAuthorizedUsers(LivestreamSession session) {
        List<Long> users = new ArrayList<>();

        if (session.getProducerId() != null) {
            users.add(session.getProducerId());
        }

        if (session.getReceiverIds() != null) {
            users.addAll(session.getReceiverIds());
        }

        return users.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }

    private void updateRecordingStatus(Recording recording) throws IOException {
        // Attendre que le fichier soit disponible
        waitForFile(recording);

        recording.setStatus("COMPLETED");
        recording.setEndedAt(LocalDateTime.now());
        recording.setDuration(calculateDuration(recording.getStartedAt(), recording.getEndedAt()));

        Path filePath = Paths.get(recording.getFilePath());
        if (Files.exists(filePath)) {
            recording.setFileSize(Files.size(filePath));
        }

        recordingRepository.save(recording);
    }

    private void waitForFile(Recording recording) {
        Path filePath = Paths.get(recording.getFilePath());
        int attempts = 10;

        while (attempts > 0 && (!Files.exists(filePath) || getFileSize(filePath) < 1024)) {
            try {
                Thread.sleep(1000); // Attendre 1 seconde
                attempts--;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        log.info("File verification completed. Exists: {}, Size: {} bytes",
                Files.exists(filePath), getFileSize(filePath));
    }

    private long getFileSize(Path filePath) {
        try {
            return Files.exists(filePath) ? Files.size(filePath) : 0;
        } catch (IOException e) {
            return 0;
        }
    }

    private void markRecordingFailed(Recording recording) {
        recording.setStatus("FAILED");
        recording.setEndedAt(LocalDateTime.now());
        recording.setDuration(calculateDuration(recording.getStartedAt(), recording.getEndedAt()));
        recordingRepository.save(recording);
    }

    private void checkDiskSpace(Path path) throws IOException {
        long usableSpace = Files.getFileStore(path).getUsableSpace();
        long requiredSpace = 100L * 1024L * 1024L; // 100MB minimum

        if (usableSpace < requiredSpace) {
            throw new IOException("Espace disque insuffisant: " + (usableSpace / 1024 / 1024) + "MB disponible");
        }
    }

    private long calculateDuration(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) return 0;
        return java.time.Duration.between(start, end).getSeconds();
    }

    public  RecordingResponse mapToResponse(Recording recording) {
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
    @Transactional(readOnly = true)
    public Map<String, List<RecordingResponse>> getProducerRecordingsGroupedBySkill(Long producerId, String token) {
        log.info("Getting recordings for producer: {}", producerId);

        // Récupérer toutes les sessions du producteur
        List<LivestreamSession> sessions = sessionRepository.findByProducerId(producerId);

        Map<String, List<RecordingResponse>> groupedRecordings = new HashMap<>();

        for (LivestreamSession session : sessions) {
            try {
                // Récupérer le nom de la compétence
                SkillResponse skill = skillServiceClient.getSkillById(session.getSkillId());
                String skillKey = skill.name() + "_" + skill.id();

                // Récupérer les enregistrements de cette session
                List<Recording> recordings = recordingRepository.findBySessionIdOrderByRecordingNumberAsc(session.getId());

                if (!recordings.isEmpty()) {
                    List<RecordingResponse> responses = recordings.stream()
                            .map(this::mapToResponse)
                            .collect(Collectors.toList());

                    groupedRecordings.computeIfAbsent(skillKey, k -> new ArrayList<>()).addAll(responses);
                }
            } catch (Exception e) {
                log.error("Error fetching skill info for session {}: {}", session.getId(), e.getMessage());
            }
        }

        return groupedRecordings;
    }

    @Transactional(readOnly = true)
    public Map<String, List<RecordingResponse>> getReceiverRecordingsGroupedBySkill(Long receiverId, String token) {
        log.info("Getting recordings for receiver: {}", receiverId);

        // Récupérer toutes les sessions où le receiver est participant
        List<LivestreamSession> sessions = sessionRepository.findByReceiverIdsContaining(receiverId);

        Map<String, List<RecordingResponse>> groupedRecordings = new HashMap<>();

        for (LivestreamSession session : sessions) {
            // Vérifier que la session est terminée
            if (!"COMPLETED".equals(session.getStatus())) {
                continue;
            }

            try {
                // Récupérer le nom de la compétence
                SkillResponse skill = skillServiceClient.getSkillById(session.getSkillId());
                String skillKey = skill.name() + "_" + skill.id();

                // Récupérer les enregistrements autorisés pour ce receiver
                List<Recording> recordings = recordingRepository.findBySessionIdOrderByRecordingNumberAsc(session.getId())
                        .stream()
                        .filter(r -> r.getAuthorizedUsers() != null && r.getAuthorizedUsers().contains(receiverId))
                        .collect(Collectors.toList());

                if (!recordings.isEmpty()) {
                    List<RecordingResponse> responses = recordings.stream()
                            .map(this::mapToResponse)
                            .collect(Collectors.toList());

                    groupedRecordings.computeIfAbsent(skillKey, k -> new ArrayList<>()).addAll(responses);
                }
            } catch (Exception e) {
                log.error("Error fetching skill info for session {}: {}", session.getId(), e.getMessage());
            }
        }

        return groupedRecordings;
    }

    @Transactional(readOnly = true)
    public List<RecordingResponse> getSkillRecordingsForUser(Integer skillId, Long userId, String token) {
        log.info("Getting recordings for skill {} and user {}", skillId, userId);

        // Récupérer toutes les sessions de cette compétence
        List<LivestreamSession> sessions = sessionRepository.findBySkillIdAndStatusIn(
                skillId,
                List.of("COMPLETED")
        );

        List<RecordingResponse> allRecordings = new ArrayList<>();

        for (LivestreamSession session : sessions) {
            // Vérifier l'autorisation
            boolean isAuthorized = session.getProducerId().equals(userId) ||
                    (session.getReceiverIds() != null && session.getReceiverIds().contains(userId));

            if (isAuthorized) {
                List<Recording> recordings = recordingRepository.findBySessionIdOrderByRecordingNumberAsc(session.getId());
                allRecordings.addAll(
                        recordings.stream()
                                .map(this::mapToResponse)
                                .collect(Collectors.toList())
                );
            }
        }

        return allRecordings;
    }

    @Transactional(readOnly = true)
    public Map<Integer, List<RecordingResponse>> getFinishedSkillsRecordings(Long userId, String token) {
        log.info("Getting finished skills recordings for user {}", userId);

        Map<Integer, List<RecordingResponse>> recordingsBySkill = new HashMap<>();

        // Récupérer les sessions terminées où l'utilisateur est receiver
        List<LivestreamSession> finishedSessions = sessionRepository
                .findByReceiverIdsContainingAndStatus(userId, "COMPLETED");

        for (LivestreamSession session : finishedSessions) {
            List<Recording> recordings = recordingRepository.findBySessionIdOrderByRecordingNumberAsc(session.getId());

            if (!recordings.isEmpty()) {
                List<RecordingResponse> responses = recordings.stream()
                        .filter(r -> r.getAuthorizedUsers() != null && r.getAuthorizedUsers().contains(userId))
                        .map(this::mapToResponse)
                        .collect(Collectors.toList());

                if (!responses.isEmpty()) {
                    recordingsBySkill.computeIfAbsent(session.getSkillId(), k -> new ArrayList<>()).addAll(responses);
                }
            }
        }

        return recordingsBySkill;
    }

}