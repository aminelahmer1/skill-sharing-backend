package com.example.serviceexchange.service;

import com.example.serviceexchange.FeignClient.SkillServiceClient;
import com.example.serviceexchange.FeignClient.UserServiceClient;
import com.example.serviceexchange.dto.*;
import com.example.serviceexchange.entity.Exchange;
import com.example.serviceexchange.entity.ExchangeStatus;
import com.example.serviceexchange.exception.*;
import com.example.serviceexchange.repository.ExchangeRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeService {
    private final ExchangeRepository exchangeRepository;
    private final SkillServiceClient skillServiceClient;
    private final UserServiceClient userServiceClient;
    private final NotificationService notificationService;
    private final ExchangeValidator exchangeValidator;

    private static final String ONLY_PRODUCER_CAN_PERFORM_ACTION = "Only the producer can perform this action";
    private static final String ONLY_RECEIVERS_CAN_CREATE_EXCHANGES = "Only receivers can create exchanges";
    private static final String YOU_CAN_ONLY_CREATE_EXCHANGES_FOR_YOURSELF = "You can only create exchanges for yourself";
    private static final String PRODUCER_MUST_HAVE_PRODUCER_ROLE = "The producer must have PRODUCER role";
    private static final String SKILL_DOES_NOT_BELONG_TO_PRODUCER = "Skill does not belong to the producer";
    private static final String NO_AVAILABLE_SLOTS = "No available slots for this skill";
    private static final String EXCHANGE_NOT_FOUND = "Exchange not found";
    private static final String NO_PARTICIPANTS = "No accepted participants for this skill";
    private static final String NO_IN_PROGRESS_PARTICIPANTS = "No in-progress participants for this skill";
    private static final String NO_PENDING_EXCHANGES = "No pending exchanges found for this skill";

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public class SkillNotFoundException extends RuntimeException {
        public SkillNotFoundException(String message) {
            super(message);
        }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public class InvalidStateException extends RuntimeException {
        public InvalidStateException(String message) {
            super(message);
        }
    }

    @Transactional
    public ExchangeResponse createExchange(ExchangeRequest request, Jwt jwt) {
        String receiverToken = "Bearer " + jwt.getTokenValue();
        log.info("Creating exchange with request: {}", request);

        UserResponse receiver = validateReceiver(request, jwt, receiverToken);
        UserResponse producer = validateProducer(request, receiverToken);
        SkillResponse skill = validateSkill(request, producer);

        if (skill.nbInscrits() >= skill.availableQuantity()) {
            throw new CapacityExceededException(NO_AVAILABLE_SLOTS);
        }

        exchangeValidator.validateExchangeCreation(request, producer, receiver);

        Exchange exchange = Exchange.builder()
                .producerId(producer.id())
                .receiverId(receiver.id())
                .skillId(skill.id())
                .status(ExchangeStatus.PENDING.toString())
                .streamingDate(parseStreamingDateTime(skill))
                .build();

        Exchange savedExchange = exchangeRepository.save(exchange);
        log.info("Saved exchange ID: {}", savedExchange.getId());

        try {
            String producerToken = getProducerToken(producer.id(), receiverToken);
            skillServiceClient.incrementInscrits(skill.id(), producerToken);
            log.info("Incremented nbInscrits for skill ID: {}", skill.id());
        } catch (Exception e) {
            log.error("Failed to increment nbInscrits for skill ID: {}. Rolling back exchange creation.", skill.id(), e);
            throw new RuntimeException("Failed to increment skill registrations", e);
        }

        notificationService.notifyNewRequest(producer, receiver, skill, savedExchange.getId());

        return toResponse(savedExchange, skill, receiver);
    }


    @Transactional
    public ExchangeResponse acceptExchange(Integer exchangeId, Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        log.info("Attempting to accept exchange ID: {}", exchangeId);
        Exchange exchange = getExchange(exchangeId);
        log.info("Found exchange: {}", exchange);
        validateProducerAction(exchange, jwt, token);
        log.info("Producer validated for exchange ID: {}", exchangeId);

        if (!"PENDING".equals(exchange.getStatus())) {
            log.warn("Exchange ID {} is not in PENDING state, current state: {}", exchangeId, exchange.getStatus());
            throw new InvalidStateException("Exchange must be in PENDING state to accept");
        }

        SkillResponse skill = fetchSkill(exchange.getSkillId());
        if (skill == null) {
            log.warn("Skill ID {} not found for exchange ID {}", exchange.getSkillId(), exchangeId);
            throw new SkillNotFoundException("Skill not found for exchange");
        }
        log.info("Fetched skill ID: {}, nbInscrits: {}, availableQuantity: {}", skill.id(), skill.nbInscrits(), skill.availableQuantity());

        // Skip capacity check since PENDING exchange already reserved a slot
        // Alternatively, validate that nbInscrits is consistent
        if (skill.nbInscrits() > skill.availableQuantity()) {
            log.error("Inconsistent state for skill ID {}: nbInscrits ({}) exceeds availableQuantity ({})",
                    skill.id(), skill.nbInscrits(), skill.availableQuantity());
            throw new CapacityExceededException("Skill capacity exceeded due to inconsistent registration count");
        }

        exchange.setStatus(ExchangeStatus.ACCEPTED.toString());
        Exchange updatedExchange = exchangeRepository.save(exchange);
        log.info("Exchange ID {} updated to status: {}", updatedExchange.getId(), updatedExchange.getStatus());

        UserResponse producer = fetchUserById(exchange.getProducerId(), token);
        UserResponse receiver = fetchUserById(exchange.getReceiverId(), token);
        notificationService.notifyRequestAccepted(receiver, producer, skill, exchangeId);
        log.info("Notification sent for accepted exchange ID: {}", exchangeId);

        return toResponse(updatedExchange, skill, receiver);
    }




    @Transactional
    public ExchangeResponse rejectExchange(Integer exchangeId, String reason, Jwt jwt) {
        String producerToken = "Bearer " + jwt.getTokenValue();
        log.info("Attempting to reject exchange ID: {}", exchangeId);
        Exchange exchange = getExchange(exchangeId);
        log.info("Found exchange: {}", exchange);
        validateProducerAction(exchange, jwt, producerToken);
        log.info("Producer validated for exchange ID: {}", exchangeId);

        if (!"PENDING".equals(exchange.getStatus())) {
            log.warn("Exchange ID {} is not in PENDING state, current state: {}", exchangeId, exchange.getStatus());
            throw new InvalidStateException("Exchange must be in PENDING state to reject");
        }

        exchange.setStatus(ExchangeStatus.REJECTED.toString());
        exchange.setRejectionReason(reason); // Ensure rejection reason is set
        Exchange updatedExchange = exchangeRepository.save(exchange);
        log.info("Exchange ID {} updated to status: {} with reason: {}", updatedExchange.getId(), updatedExchange.getStatus(), reason);
        log.debug("Saved exchange with rejectionReason: {}", updatedExchange.getRejectionReason()); // Debug log

        try {
            String token = getProducerToken(exchange.getProducerId(), producerToken);
            skillServiceClient.decrementInscrits(exchange.getSkillId(), token);
            log.info("Decremented nbInscrits for skill ID: {}", exchange.getSkillId());
        } catch (Exception e) {
            log.error("Failed to decrement nbInscrits for skill ID: {}. Proceeding with rejection.", exchange.getSkillId(), e);
        }

        UserResponse producer = fetchUserById(exchange.getProducerId(), producerToken);
        UserResponse receiver = fetchUserById(exchange.getReceiverId(), producerToken);
        SkillResponse skill = fetchSkill(exchange.getSkillId());
        if (skill == null) {
            log.warn("Skill ID {} not found for exchange ID {}, using default name for notification", exchange.getSkillId(), exchangeId);
            skill = new SkillResponse(exchange.getSkillId(), "Deleted Skill", null, 0, null, 0, null, null, null, null, null, null, null);
        }
        notificationService.notifyRequestRejected(receiver, producer, skill, reason, exchangeId);
        log.info("Notification sent for rejected exchange ID: {}", exchangeId);

        return toResponse(updatedExchange, skill, receiver);
    }

    @Transactional
    public void startSession(Integer skillId, Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        UserResponse user = fetchUserByKeycloakId(jwt.getSubject(), token);
        SkillResponse skill = fetchSkill(skillId);
        if (skill == null) {
            log.warn("Skill ID {} not found", skillId);
            throw new SkillNotFoundException("Skill not found");
        }

        if (!user.id().equals(skill.userId())) {
            throw new AccessDeniedException(ONLY_PRODUCER_CAN_PERFORM_ACTION);
        }

        List<Exchange> exchanges = exchangeRepository.findBySkillIdAndStatus(skillId, ExchangeStatus.ACCEPTED.toString());
        if (exchanges.isEmpty()) {
            throw new NoParticipantsException(NO_PARTICIPANTS);
        }

        exchanges.forEach(exchange -> {
            exchange.setStatus(ExchangeStatus.IN_PROGRESS.toString());
            exchangeRepository.save(exchange);
            UserResponse receiver = fetchUserById(exchange.getReceiverId(), token);
            notificationService.notifySessionStarted(receiver, user, skillId, exchange.getId());
        });
    }

    @Transactional
    public void completeSession(Integer skillId, Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        UserResponse user = fetchUserByKeycloakId(jwt.getSubject(), token);
        SkillResponse skill = fetchSkill(skillId);
        if (skill == null) {
            log.warn("Skill ID {} not found", skillId);
            throw new SkillNotFoundException("Skill not found");
        }

        if (!user.id().equals(skill.userId())) {
            throw new AccessDeniedException(ONLY_PRODUCER_CAN_PERFORM_ACTION);
        }

        List<Exchange> exchanges = exchangeRepository.findBySkillIdAndStatus(skillId, ExchangeStatus.IN_PROGRESS.toString());
        if (exchanges.isEmpty()) {
            throw new NoParticipantsException(NO_IN_PROGRESS_PARTICIPANTS);
        }

        exchanges.forEach(exchange -> {
            exchange.setStatus(ExchangeStatus.COMPLETED.toString());
            exchangeRepository.save(exchange);
            UserResponse receiver = fetchUserById(exchange.getReceiverId(), token);
            notificationService.notifySessionCompleted(receiver, user, skillId, exchange.getId());
        });
    }

    @Transactional(readOnly = true)
    public List<ExchangeResponse> getUserExchanges(Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        UserResponse user = fetchUserByKeycloakId(jwt.getSubject(), token);
        List<Exchange> exchanges = exchangeRepository.findUserExchanges(user.id());
        if (exchanges.isEmpty()) {
            log.info("No exchanges found for user ID: {}", user.id());
            return List.of();
        }

        return exchanges.stream()
                .map(exchange -> {
                    SkillResponse skill = fetchSkill(exchange.getSkillId());
                    if (skill == null) {
                        log.warn("Skipping exchange ID {} due to unavailable skill ID {}", exchange.getId(), exchange.getSkillId());
                        return null;
                    }
                    UserResponse receiver = fetchUserById(exchange.getReceiverId(), token);
                    return toResponse(exchange, skill, receiver);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private Exchange getExchange(Integer exchangeId) {
        return exchangeRepository.findById(exchangeId)
                .orElseThrow(() -> new ExchangeNotFoundException(EXCHANGE_NOT_FOUND));
    }

    private UserResponse fetchUserById(Long userId, String token) {
        log.info("Fetching user with ID: {} using token", userId);
        try {
            UserResponse userResponse = userServiceClient.getUserById(userId, token);
            log.info("Received user response: {}", userResponse);
            return userResponse;
        } catch (Exception e) {
            log.error("Failed to fetch user with ID {}: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    private UserResponse fetchUserByKeycloakId(String keycloakId, String token) {
        try {
            return userServiceClient.getUserByKeycloakId(keycloakId, token);
        } catch (Exception e) {
            log.error("Failed to fetch user with Keycloak ID {}: {}", keycloakId, e.getMessage(), e);
            throw e;
        }
    }

    private SkillResponse fetchSkill(Integer skillId) {
        try {
            return skillServiceClient.getSkillById(skillId);
        } catch (FeignException.NotFound e) {
            log.warn("Skill ID {} not found", skillId);
            return null; // Compétence non trouvée, on passe à l'échange suivant
        } catch (FeignException.InternalServerError e) {
            log.error("Internal server error fetching skill with ID {}: {}", skillId, e.getMessage());
            return null; // Erreur interne, on passe à l'échange suivant
        } catch (FeignException e) {
            log.error("Error fetching skill with ID {}: status={}, message={}", skillId, e.status(), e.getMessage());
            return null; // Autre erreur Feign, on passe à l'échange suivant
        } catch (Exception e) {
            log.error("Unexpected error fetching skill with ID {}: {}", skillId, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch skill", e); // Erreur inattendue, on propage l'exception
        }
    }

    private LocalDateTime parseStreamingDateTime(SkillResponse skill) {
        try {
            return LocalDateTime.parse(skill.streamingDate() + "T" + skill.streamingTime());
        } catch (Exception e) {
            log.error("Failed to parse streaming date/time for skill {}: {}", skill.id(), e.getMessage(), e);
            throw new IllegalArgumentException("Invalid streaming date/time format", e);
        }
    }

    private void validateProducerAction(Exchange exchange, Jwt jwt, String token) {
        UserResponse user = fetchUserByKeycloakId(jwt.getSubject(), token);
        if (!user.id().equals(exchange.getProducerId())) {
            throw new AccessDeniedException(ONLY_PRODUCER_CAN_PERFORM_ACTION);
        }
    }

    private UserResponse validateReceiver(ExchangeRequest request, Jwt jwt, String token) {
        UserResponse receiver = fetchUserByKeycloakId(jwt.getSubject(), token);
        if (!receiver.roles().contains("RECEIVER")) {
            throw new AccessDeniedException(ONLY_RECEIVERS_CAN_CREATE_EXCHANGES);
        }
        if (!receiver.id().equals(request.receiverId())) {
            throw new AccessDeniedException(YOU_CAN_ONLY_CREATE_EXCHANGES_FOR_YOURSELF);
        }
        return receiver;
    }

    private UserResponse validateProducer(ExchangeRequest request, String token) {
        UserResponse producer = fetchUserById(request.producerId(), token);
        if (!producer.roles().contains("PRODUCER")) {
            throw new InvalidExchangeException(PRODUCER_MUST_HAVE_PRODUCER_ROLE);
        }
        return producer;
    }

    private SkillResponse validateSkill(ExchangeRequest request, UserResponse producer) {
        SkillResponse skill = fetchSkill(request.skillId());
        if (skill == null) {
            throw new SkillNotFoundException("Skill not found");
        }
        if (!producer.id().equals(skill.userId())) {
            throw new InvalidExchangeException(SKILL_DOES_NOT_BELONG_TO_PRODUCER);
        }
        return skill;
    }

    private ExchangeResponse toResponse(Exchange exchange, SkillResponse skill, UserResponse receiver) {
        return new ExchangeResponse(
                exchange.getId(),
                exchange.getProducerId(),
                exchange.getReceiverId(),
                exchange.getSkillId(),
                exchange.getStatus(),
                exchange.getCreatedAt(),
                exchange.getUpdatedAt(),
                exchange.getStreamingDate(),
                exchange.getProducerRating(),
                exchange.getRejectionReason(),
                skill.name(),
                receiver.firstName() + " " + receiver.lastName(),
                skill.id()
        );
    }
    private String getProducerToken(Long producerId, String fallbackToken) {
        try {
            UserResponse producer = fetchUserById(producerId, fallbackToken);
            if (producer.roles().contains("PRODUCER")) {
                log.info("Using fallback token for producer ID: {}", producerId);
                return fallbackToken;
            }
            throw new RuntimeException("Producer token not available");
        } catch (Exception e) {
            log.error("Failed to obtain producer token for producer ID {}: {}", producerId, e.getMessage(), e);
            throw new RuntimeException("Unable to obtain producer token", e);
        }
    }

    @Transactional(readOnly = true)
    public List<ExchangeResponse> getPendingExchangesForProducer(Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        UserResponse user = fetchUserByKeycloakId(jwt.getSubject(), token);
        log.info("Fetching pending exchanges for producer ID: {}", user.id());
        List<Exchange> pendingExchanges = exchangeRepository.findByProducerIdAndStatus(user.id(), ExchangeStatus.PENDING.toString());
        log.info("Found {} pending exchanges", pendingExchanges.size());

        return pendingExchanges.stream()
                .map(exchange -> {
                    log.info("Processing exchange ID: {}", exchange.getId());
                    SkillResponse skill = fetchSkill(exchange.getSkillId());
                    if (skill == null) {
                        log.warn("Skipping exchange ID {} due to unavailable skill ID: {}", exchange.getId(), exchange.getSkillId());
                        return null;
                    }
                    UserResponse receiver = fetchUserById(exchange.getReceiverId(), token);
                    if (receiver == null) {
                        log.warn("Skipping exchange ID {} due to unavailable receiver ID: {}", exchange.getId(), exchange.getReceiverId());
                        return null;
                    }
                    return toResponse(exchange, skill, receiver);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    @Transactional
    public List<ExchangeResponse> acceptAllPendingExchanges(Integer skillId, Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        UserResponse user = fetchUserByKeycloakId(jwt.getSubject(), token);
        SkillResponse skill = fetchSkill(skillId);
        if (skill == null) {
            log.warn("Skill ID {} not found", skillId);
            throw new SkillNotFoundException("Skill not found");
        }

        if (!user.id().equals(skill.userId())) {
            throw new AccessDeniedException(ONLY_PRODUCER_CAN_PERFORM_ACTION);
        }

        List<Exchange> pendingExchanges = exchangeRepository.findBySkillIdAndStatus(skillId, ExchangeStatus.PENDING.toString());
        if (pendingExchanges.isEmpty()) {
            throw new NoParticipantsException(NO_PENDING_EXCHANGES);
        }

        int availableSlots = skill.availableQuantity() - skill.nbInscrits();
        if (availableSlots < pendingExchanges.size()) {
            throw new CapacityExceededException(String.format(
                    "Not enough slots available: %d needed, %d available", pendingExchanges.size(), availableSlots));
        }

        List<ExchangeResponse> acceptedExchanges = pendingExchanges.stream().map(exchange -> {
            exchange.setStatus(ExchangeStatus.ACCEPTED.toString());
            Exchange updatedExchange = exchangeRepository.save(exchange);
            log.info("Exchange ID {} updated to status: {}", updatedExchange.getId(), updatedExchange.getStatus());

            UserResponse producer = fetchUserById(exchange.getProducerId(), token);
            UserResponse receiver = fetchUserById(exchange.getReceiverId(), token);
            notificationService.notifyRequestAccepted(receiver, producer, skill, exchange.getId());

            return toResponse(updatedExchange, skill, receiver);
        }).collect(Collectors.toList());

        log.info("Accepted {} pending exchanges for skill ID: {}", acceptedExchanges.size(), skillId);
        return acceptedExchanges;
    }
}