package com.example.serviceexchange.service;

import com.example.serviceexchange.FeignClient.SkillServiceClient;
import com.example.serviceexchange.FeignClient.UserServiceClient;
import com.example.serviceexchange.dto.*;
import com.example.serviceexchange.entity.Exchange;
import com.example.serviceexchange.entity.ExchangeStatus;
import com.example.serviceexchange.exception.*;
import com.example.serviceexchange.repository.ExchangeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
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
                .status(ExchangeStatus.PENDING)
                .streamingDate(parseStreamingDateTime(skill))
                .build();

        Exchange savedExchange = exchangeRepository.save(exchange);

        try {
            String producerToken = getProducerToken(producer.id(), receiverToken);
            skillServiceClient.incrementInscrits(skill.id(), producerToken);
            log.info("Incremented nbInscrits for skill ID: {}", skill.id());
        } catch (Exception e) {
            log.error("Failed to increment nbInscrits for skill ID: {}. Rolling back exchange creation.", skill.id(), e);
            throw new RuntimeException("Failed to increment skill registrations", e);
        }

        notificationService.notifyNewRequest(producer, receiver, skill, savedExchange.getId());

        return toResponse(savedExchange);
    }

    @Transactional
    public ExchangeResponse acceptExchange(Integer exchangeId, Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        Exchange exchange = getExchange(exchangeId);
        validateProducerAction(exchange, jwt, token);

        SkillResponse skill = fetchSkill(exchange.getSkillId());
        if (skill.nbInscrits() >= skill.availableQuantity()) {
            throw new CapacityExceededException(NO_AVAILABLE_SLOTS);
        }

        exchange.setStatus(ExchangeStatus.ACCEPTED);
        Exchange updatedExchange = exchangeRepository.save(exchange);

        UserResponse producer = fetchUserById(exchange.getProducerId(), token);
        UserResponse receiver = fetchUserById(exchange.getReceiverId(), token);
        notificationService.notifyRequestAccepted(receiver, producer, skill, exchangeId);

        return toResponse(updatedExchange);
    }

    @Transactional
    public ExchangeResponse rejectExchange(Integer exchangeId, String reason, Jwt jwt) {
        String producerToken = "Bearer " + jwt.getTokenValue();
        Exchange exchange = getExchange(exchangeId);
        validateProducerAction(exchange, jwt, producerToken);

        exchange.setStatus(ExchangeStatus.REJECTED);
        Exchange updatedExchange = exchangeRepository.save(exchange);

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
        notificationService.notifyRequestRejected(receiver, producer, skill, reason, exchangeId);

        return toResponse(updatedExchange);
    }

    @Transactional
    public void startSession(Integer skillId, Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        UserResponse user = fetchUserByKeycloakId(jwt.getSubject(), token);
        SkillResponse skill = fetchSkill(skillId);

        if (!user.id().equals(skill.userId())) {
            throw new AccessDeniedException(ONLY_PRODUCER_CAN_PERFORM_ACTION);
        }

        List<Exchange> exchanges = exchangeRepository.findBySkillIdAndStatus(skillId, ExchangeStatus.ACCEPTED);

        if (exchanges.isEmpty()) {
            throw new NoParticipantsException(NO_PARTICIPANTS);
        }

        exchanges.forEach(exchange -> {
            exchange.setStatus(ExchangeStatus.IN_PROGRESS);
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

        if (!user.id().equals(skill.userId())) {
            throw new AccessDeniedException(ONLY_PRODUCER_CAN_PERFORM_ACTION);
        }

        List<Exchange> exchanges = exchangeRepository.findBySkillIdAndStatus(skillId, ExchangeStatus.IN_PROGRESS);

        if (exchanges.isEmpty()) {
            throw new NoParticipantsException(NO_IN_PROGRESS_PARTICIPANTS);
        }

        exchanges.forEach(exchange -> {
            exchange.setStatus(ExchangeStatus.COMPLETED);
            exchangeRepository.save(exchange);

            UserResponse receiver = fetchUserById(exchange.getReceiverId(), token);
            notificationService.notifySessionCompleted(receiver, user, skillId, exchange.getId());
        });
    }

    public List<ExchangeResponse> getUserExchanges(Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        UserResponse user = fetchUserByKeycloakId(jwt.getSubject(), token);
        return exchangeRepository.findUserExchanges(user.id()).stream()
                .map(this::toResponse)
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
        } catch (Exception e) {
            log.error("Failed to fetch skill with ID {}: {}", skillId, e.getMessage(), e);
            throw e;
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
        if (!producer.id().equals(skill.userId())) {
            throw new InvalidExchangeException(SKILL_DOES_NOT_BELONG_TO_PRODUCER);
        }
        return skill;
    }

    private ExchangeResponse toResponse(Exchange exchange) {
        return new ExchangeResponse(
                exchange.getId(),
                exchange.getProducerId(),
                exchange.getReceiverId(),
                exchange.getSkillId(),
                exchange.getStatus().toString(),
                exchange.getCreatedAt(),
                exchange.getUpdatedAt(),
                exchange.getStreamingDate(),
                exchange.getProducerRating(),
                null
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
    public List<SkillResponse> getPendingExchangesForProducer(Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        UserResponse user = fetchUserByKeycloakId(jwt.getSubject(), token);

        if (!user.roles().contains("PRODUCER")) {
            throw new AccessDeniedException(PRODUCER_MUST_HAVE_PRODUCER_ROLE);
        }

        // Fetch pending exchanges for the producer
        List<Exchange> pendingExchanges = exchangeRepository.findByProducerIdAndStatus(user.id(), ExchangeStatus.PENDING);
        if (pendingExchanges.isEmpty()) {
            log.info("No pending exchanges found for producer ID: {}", user.id());
            return List.of();
        }

        // Get unique skill IDs from pending exchanges
        List<Integer> skillIds = pendingExchanges.stream()
                .map(Exchange::getSkillId)
                .distinct()
                .collect(Collectors.toList());

        // Fetch skills for the identified skill IDs
        List<SkillResponse> skills = skillIds.stream()
                .map(skillId -> skillServiceClient.getSkillById(skillId))
                .filter(skill -> skill.userId().equals(user.id())) // Ensure skills belong to the producer
                .collect(Collectors.toList());

        log.info("Found {} skills with pending exchanges for producer ID: {}", skills.size(), user.id());
        return skills;
    }

    @Transactional
    public List<ExchangeResponse> acceptAllPendingExchanges(Integer skillId, Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        UserResponse user = fetchUserByKeycloakId(jwt.getSubject(), token);
        SkillResponse skill = fetchSkill(skillId);

        if (!user.id().equals(skill.userId())) {
            throw new AccessDeniedException(ONLY_PRODUCER_CAN_PERFORM_ACTION);
        }

        List<Exchange> pendingExchanges = exchangeRepository.findBySkillIdAndStatus(skillId, ExchangeStatus.PENDING);
        if (pendingExchanges.isEmpty()) {
            throw new NoParticipantsException(NO_PENDING_EXCHANGES);
        }

        int availableSlots = skill.availableQuantity() - skill.nbInscrits();
        if (availableSlots < pendingExchanges.size()) {
            throw new CapacityExceededException(String.format(
                    "Not enough slots available: %d needed, %d available", pendingExchanges.size(), availableSlots));
        }

        List<ExchangeResponse> acceptedExchanges = pendingExchanges.stream().map(exchange -> {
            exchange.setStatus(ExchangeStatus.ACCEPTED);
            Exchange updatedExchange = exchangeRepository.save(exchange);

            UserResponse producer = fetchUserById(exchange.getProducerId(), token);
            UserResponse receiver = fetchUserById(exchange.getReceiverId(), token);
            notificationService.notifyRequestAccepted(receiver, producer, skill, exchange.getId());

            return toResponse(updatedExchange);
        }).collect(Collectors.toList());

        log.info("Accepted {} pending exchanges for skill ID: {}", acceptedExchanges.size(), skillId);
        return acceptedExchanges;
    }

}