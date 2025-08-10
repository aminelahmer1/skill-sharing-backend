package com.example.servicelivestream.service;

import com.example.servicelivestream.dto.*;
import com.example.servicelivestream.entity.LivestreamSession;
import com.example.servicelivestream.entity.Recording;
import com.example.servicelivestream.feignclient.ExchangeServiceClient;
import com.example.servicelivestream.feignclient.SkillServiceClient;
import com.example.servicelivestream.feignclient.UserServiceClient;
import com.example.servicelivestream.repository.LivestreamSessionRepository;
import com.example.servicelivestream.repository.RecordingRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class LivestreamService {
    private final LivestreamSessionRepository sessionRepository;
    private final RecordingRepository recordingRepository;
    private final ExchangeServiceClient exchangeServiceClient;
    private final UserServiceClient userServiceClient;
    private final SkillServiceClient skillServiceClient;
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;
    private final LiveKitService liveKitService;

    @Value("${application.livestream.allow-sessions-without-participants:true}")
    private boolean allowSessionsWithoutParticipants;

    @Transactional(readOnly = true)
    public LivestreamSession getSessionBySkillId(Integer skillId, Jwt jwt) {
        // 1. Récupérer l'utilisateur courant
        UserResponse user = fetchUserByKeycloakId(jwt.getSubject(), "Bearer " + jwt.getTokenValue());

        // 2. Trouver les sessions actives pour cette compétence
        List<LivestreamSession> sessions = sessionRepository.findBySkillIdAndStatusIn(
                skillId, List.of("SCHEDULED", "LIVE"));

        if (sessions.isEmpty()) {
            log.warn("No active session found for skill: {}", skillId);
            return null;
        }

        // Prendre la première session active (normalement il ne devrait y en avoir qu'une)
        LivestreamSession session = sessions.get(0);

        // 3. Vérifier si l'utilisateur est autorisé
        if (!isAuthorizedForSession(session, user.id())) {
            throw new ResponseStatusException(FORBIDDEN, "Not authorized to access this session");
        }

        return session;
    }


    @Transactional
    public LivestreamSession startSession(Integer skillId, Jwt jwt, boolean immediate) {
        String token = "Bearer " + jwt.getTokenValue();
        log.info("Starting session for skillId: {}, immediate: {}", skillId, immediate);
        List<LivestreamSession> completedSessions = sessionRepository.findBySkillIdAndStatusIn(
                skillId, List.of("COMPLETED")
        );
        if (!completedSessions.isEmpty()) {
            log.error("Session already completed for skill: {}", skillId);
            throw new ResponseStatusException(CONFLICT,
                    "Une session a déjà été complétée pour cette compétence. Impossible d'en créer une nouvelle.");
        }
        // 1. Validation des paramètres
        validateSkillId(skillId);
        checkExistingActiveSessions(skillId);

        // 2. Récupération des données
        UserResponse producer = fetchUserByKeycloakId(jwt.getSubject(), token);
        SkillResponse skill = fetchSkill(skillId);
        validateProducerOwnsSkill(producer, skill);

        // 3. Gestion des participants
        List<ExchangeResponse> acceptedExchanges = getAcceptedExchanges(skillId, token);
        if (!allowSessionsWithoutParticipants && acceptedExchanges.isEmpty()) {
            log.error("No accepted exchanges and configuration forbids empty sessions");
            throw new ResponseStatusException(BAD_REQUEST, "No accepted exchanges for this skill");
        }

        // 4. Création de la session
        String roomName = "skill_" + skillId + "_" + System.currentTimeMillis();
        String producerToken = liveKitService.generateToken(
                producer.id().toString(),
                roomName,
                true
        );

        LocalDateTime startTime = getStartTime(immediate, skill);
        LivestreamSession session = createAndSaveSession(
                skillId, producer.id(), acceptedExchanges,
                roomName, immediate, startTime, producerToken
        );

        // 5. Mise à jour et notifications
        if (!acceptedExchanges.isEmpty()) {
            updateExchangesAndNotify(acceptedExchanges, token, immediate, session, producer, skill);
        } else {
            log.warn("Starting session without participants for skill: {}", skillId);
        }

        return session;
    }

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void startScheduledSessions() {
        LocalDateTime now = LocalDateTime.now();
        log.info("Checking for scheduled sessions to start at {}", now);

        sessionRepository.findByStatusAndStartTimeBefore("SCHEDULED", now)
                .forEach(this::processScheduledSession);
    }

    @Transactional
    public void endSession(Long sessionId, Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        log.info("Ending session: {}", sessionId);

        LivestreamSession session = getSessionById(sessionId);
        UserResponse producer = fetchUserByKeycloakId(jwt.getSubject(), token);
        validateProducerCanEndSession(session, producer);

        session.setStatus("COMPLETED");
        session.setEndTime(LocalDateTime.now());
        sessionRepository.save(session);

        updateCompletedExchanges(session, token);
        sendCompletionNotification(session, producer);
    }

    @Transactional(readOnly = true)
    public LivestreamSession getSession(Long sessionId, Jwt jwt) {
        LivestreamSession session = getSessionById(sessionId);
        UserResponse user = fetchUserByKeycloakId(jwt.getSubject(), "Bearer " + jwt.getTokenValue());

        if (!isAuthorizedForSession(session, user.id())) {
            throw new ResponseStatusException(FORBIDDEN, "Not authorized to access this session");
        }

        return session;
    }

    // Dans LivestreamService (service-livestream.service.ts)
    @Transactional(readOnly = true)
    public String getJoinToken(Long sessionId, Jwt jwt, boolean isProducer) {
        LivestreamSession session = getAuthorizedSession(sessionId, jwt);
        validateSessionIsLive(session);

        UserResponse user = fetchUserByKeycloakId(jwt.getSubject(), "Bearer " + jwt.getTokenValue());

        if (isProducer && !user.id().equals(session.getProducerId())) {
            throw new ResponseStatusException(FORBIDDEN, "Only producer can get producer token");
        }

        return liveKitService.generateToken(
                user.id().toString(),
                session.getRoomName(),
                isProducer // true pour producteur, false pour receveur
        );
    }

    @Transactional
    public void handleMediaServerEvent(WebhookEvent event) {
        if ("recordingFinished".equals(event.action())) {
            handleRecordingFinished(event);
        }
    }

    // Helper methods
    private boolean isAuthorizedForSession(LivestreamSession session, Long userId) {
        return session.getProducerId().equals(userId) ||
                (session.getReceiverIds() != null && session.getReceiverIds().contains(userId));
    }

    private void validateSkillId(Integer skillId) {
        if (skillId == null || skillId <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid skill ID");
        }
    }

    private void checkExistingActiveSessions(Integer skillId) {
        List<LivestreamSession> activeSessions = sessionRepository.findBySkillIdAndStatusIn(
                skillId, List.of("SCHEDULED", "LIVE"));

        if (!activeSessions.isEmpty()) {
            String sessionIds = activeSessions.stream()
                    .map(s -> s.getId().toString())
                    .collect(Collectors.joining(", "));

            log.warn("Active session(s) already exist for skill {}: {}", skillId, sessionIds);
            throw new ResponseStatusException(CONFLICT, "Active session already exists for this skill");
        }
    }

    private void validateProducerOwnsSkill(UserResponse producer, SkillResponse skill) {
        if (!producer.id().equals(skill.userId())) {
            throw new ResponseStatusException(FORBIDDEN, "Only skill owner can start session");
        }
    }

    private List<ExchangeResponse> getAcceptedExchanges(Integer skillId, String token) {
        try {
            return exchangeServiceClient.getExchangesBySkillId(skillId, token).stream()
                    .filter(e -> "ACCEPTED".equals(e.status()))
                    .collect(Collectors.toList());
        } catch (FeignException e) {
            log.error("Failed to fetch exchanges for skill {}: {}", skillId, e.contentUTF8());
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Error fetching exchanges for skill {}: {}", skillId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private String generateRoomName(Integer skillId) {
        return "skill_" + skillId + "_" + System.currentTimeMillis();
    }

    private LocalDateTime getStartTime(boolean immediate, SkillResponse skill) {
        try {
            return immediate ? LocalDateTime.now() :
                    LocalDateTime.parse(skill.streamingDate() + "T" + skill.streamingTime());
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid date/time format", e);
        } catch (NullPointerException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Missing date/time information", e);
        }
    }

    private LivestreamSession createAndSaveSession(Integer skillId, Long producerId,
                                                   List<ExchangeResponse> exchanges, String roomName,
                                                   boolean immediate, LocalDateTime startTime,
                                                   String producerToken) {
        LivestreamSession session = LivestreamSession.builder()
                .skillId(skillId)
                .producerId(producerId)
                .receiverIds(exchanges.stream()
                        .map(ExchangeResponse::receiverId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()))
                .roomName(roomName)
                .status(immediate ? "LIVE" : "SCHEDULED")
                .startTime(startTime)
                .producerToken(producerToken)
                .build();

        return sessionRepository.save(session);
    }

    private void updateExchangesAndNotify(List<ExchangeResponse> exchanges, String token,
                                          boolean immediate, LivestreamSession session,
                                          UserResponse producer, SkillResponse skill) {
        exchanges.forEach(exchange -> {
            try {
                String status = immediate ? "IN_PROGRESS" : "SCHEDULED";
                exchangeServiceClient.updateExchangeStatus(exchange.id(), status, token);

                if (immediate) {
                    notifyReceiver(session, producer, skill, exchange.receiverId(), token);
                }
            } catch (Exception e) {
                log.error("Failed to update exchange {}: {}", exchange.id(), e.getMessage());
            }
        });
    }


    private void notifyReceiver(LivestreamSession session, UserResponse producer,
                                SkillResponse skill, Long receiverId, String token) {
        try {
            UserResponse receiver = fetchUserById(receiverId, token);
            kafkaTemplate.send("notifications", new NotificationEvent(
                    "LIVESTREAM_STARTED",
                    session.getId().intValue(),
                    producer.id(),
                    receiver.id(),
                    skill.name(),
                    null,
                    session.getStartTime().toString()
            ));
        } catch (Exception e) {
            log.error("Failed to notify receiver {}: {}", receiverId, e.getMessage());
        }
    }


    private void processScheduledSession(LivestreamSession session) {
        try {
            log.info("Processing scheduled session: {}", session.getId());

            session.setStatus("LIVE");

            if (session.getProducerToken() == null || session.getProducerToken().isEmpty()) {
                String producerToken = liveKitService.generateToken(
                        session.getProducerId().toString(),
                        session.getRoomName(),
                        true
                );
                session.setProducerToken(producerToken);
            }

            LivestreamSession updatedSession = sessionRepository.save(session);

            if (session.getReceiverIds() != null && !session.getReceiverIds().isEmpty()) {
                updateScheduledExchanges(updatedSession);
                notifyParticipants(updatedSession);
            } else {
                log.info("No participants for scheduled session {}", session.getId());
            }

            log.info("Successfully started scheduled session: {}", session.getId());
        } catch (Exception e) {
            log.error("Failed to start session {}: {}", session.getId(), e.getMessage(), e);
            session.setStatus("ERROR");
            sessionRepository.save(session);
        }
    }

    private void updateScheduledExchanges(LivestreamSession session) {
        try {
            List<ExchangeResponse> exchanges = fetchExchangesBySkillId(session.getSkillId(), null);
            exchanges.stream()
                    .filter(e -> "SCHEDULED".equals(e.status()))
                    .forEach(exchange -> {
                        try {
                            exchangeServiceClient.updateExchangeStatus(
                                    exchange.id(), "IN_PROGRESS", null);
                        } catch (Exception e) {
                            log.error("Failed to update exchange {}: {}", exchange.id(), e.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to fetch exchanges for scheduled session {}: {}", session.getId(), e.getMessage());
        }
    }

    private void notifyParticipants(LivestreamSession session) {
        try {
            SkillResponse skill = fetchSkill(session.getSkillId());
            List<ExchangeResponse> exchanges = fetchExchangesBySkillId(session.getSkillId(), null);

            exchanges.forEach(exchange -> {
                try {
                    UserResponse receiver = fetchUserById(exchange.receiverId(), null);
                    kafkaTemplate.send("notifications", new NotificationEvent(
                            "LIVESTREAM_STARTED",
                            session.getId().intValue(),
                            session.getProducerId(),
                            receiver.id(),
                            skill.name(),
                            null,
                            session.getStartTime().toString()
                    ));
                } catch (Exception e) {
                    log.error("Failed to notify participant for exchange {}: {}", exchange.id(), e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Failed to notify participants for session {}: {}", session.getId(), e.getMessage());
        }
    }

    private LivestreamSession getSessionById(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Session not found"));
    }

    private void validateProducerCanEndSession(LivestreamSession session, UserResponse producer) {
        if (!producer.id().equals(session.getProducerId())) {
            throw new ResponseStatusException(FORBIDDEN, "Only producer can end session");
        }
        if (!"LIVE".equals(session.getStatus())) {
            throw new IllegalStateException("Session is not live");
        }
    }

    private void updateCompletedExchanges(LivestreamSession session, String token) {
        try {
            List<ExchangeResponse> exchanges = fetchExchangesBySkillId(session.getSkillId(), token);
            exchanges.stream()
                    .filter(e -> "IN_PROGRESS".equals(e.status()))
                    .forEach(exchange -> {
                        try {
                            exchangeServiceClient.updateExchangeStatus(
                                    exchange.id(), "COMPLETED", token);
                        } catch (Exception e) {
                            log.error("Failed to update exchange {}: {}", exchange.id(), e.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to update exchanges for completed session {}: {}", session.getId(), e.getMessage());
        }
    }

    private void sendCompletionNotification(LivestreamSession session, UserResponse producer) {
        try {
            SkillResponse skill = fetchSkill(session.getSkillId());
            kafkaTemplate.send("notifications", new NotificationEvent(
                    "LIVESTREAM_ENDED",
                    null,
                    producer.id(),
                    null,
                    skill.name(),
                    null,
                    session.getEndTime().toString()
            ));
        } catch (Exception e) {
            log.error("Failed to send completion notification for session {}: {}", session.getId(), e.getMessage());
        }
    }

    private LivestreamSession getAuthorizedSession(Long sessionId, Jwt jwt) {
        LivestreamSession session = getSessionById(sessionId);
        UserResponse user = fetchUserByKeycloakId(jwt.getSubject(), "Bearer " + jwt.getTokenValue());

        if (!isAuthorizedForSession(session, user.id())) {
            throw new ResponseStatusException(FORBIDDEN, "Not authorized to access this session");
        }

        return session;
    }

    private void validateSessionIsLive(LivestreamSession session) {
        if (!"LIVE".equals(session.getStatus())) {
            throw new IllegalStateException("Session is not live");
        }
    }

    private void handleRecordingFinished(WebhookEvent event) {
        try {
            LivestreamSession session = sessionRepository.findByRoomName(event.streamId());
            if (session != null) {
                saveRecording(session, event.recordingFilePath());
            } else {
                log.warn("No session found for room {}", event.streamId());
            }
        } catch (Exception e) {
            log.error("Failed to handle recording event: {}", e.getMessage());
        }
    }

    private void saveRecording(LivestreamSession session, String recordingPath) {
        session.setRecordingPath(recordingPath);
        sessionRepository.save(session);

        Recording recording = Recording.builder()
                .session(session)
                .filePath(recordingPath)
                .createdAt(LocalDateTime.now())
                .authorizedUsers(session.getReceiverIds())
                .build();
        recordingRepository.save(recording);
    }

    // Data access helper methods
    private UserResponse fetchUserById(Long userId, String token) {
        try {
            return userServiceClient.getUserById(userId, token);
        } catch (FeignException e) {
            log.error("Feign error fetching user {}: {}", userId, e.contentUTF8());
            throw new ResponseStatusException(valueOf(e.status()), "User service error");
        } catch (Exception e) {
            log.error("Failed to fetch user {}: {}", userId, e.getMessage());
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Failed to fetch user");
        }
    }

    private UserResponse fetchUserByKeycloakId(String keycloakId, String token) {
        try {
            return userServiceClient.getUserByKeycloakId(keycloakId, token);
        } catch (FeignException e) {
            log.error("Feign error fetching user by Keycloak ID {}: {}", keycloakId, e.contentUTF8());
            throw new ResponseStatusException(valueOf(e.status()), "User service error");
        } catch (Exception e) {
            log.error("Failed to fetch user by Keycloak ID {}: {}", keycloakId, e.getMessage());
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Failed to fetch user");
        }
    }

    private SkillResponse fetchSkill(Integer skillId) {
        try {
            return skillServiceClient.getSkillById(skillId);
        } catch (FeignException e) {
            log.error("Feign error fetching skill {}: {}", skillId, e.contentUTF8());
            throw new ResponseStatusException(valueOf(e.status()), "Skill service error");
        } catch (Exception e) {
            log.error("Failed to fetch skill {}: {}", skillId, e.getMessage());
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Failed to fetch skill");
        }
    }

    private List<ExchangeResponse> fetchExchangesBySkillId(Integer skillId, String token) {
        try {
            return exchangeServiceClient.getExchangesBySkillId(skillId, token);
        } catch (FeignException e) {
            log.error("Feign error fetching exchanges for skill {}: {}", skillId, e.contentUTF8());
            throw new ResponseStatusException(valueOf(e.status()), "Exchange service error");
        } catch (Exception e) {
            log.error("Failed to fetch exchanges for skill {}: {}", skillId, e.getMessage());
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Failed to fetch exchanges");
        }
    }
}