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
import java.util.*;
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

    private UserResponse getAuthenticatedUser(Jwt jwt) {
        String keycloakId = jwt.getSubject();
        String token = "Bearer " + jwt.getTokenValue();
        UserResponse user = userServiceClient.getUserByKeycloakId(keycloakId, token);

        if (user == null) {
            throw new RuntimeException("User not found for Keycloak ID: " + keycloakId);
        }

        return user;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAcceptedReceiversForSkill(Integer skillId, Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        UserResponse producer = getAuthenticatedUser(jwt);

        // Vérifier que la compétence appartient au producteur
        SkillResponse skill = fetchSkill(skillId);
        if (skill == null) {
            throw new SkillNotFoundException("Skill not found with ID: " + skillId);
        }
        if (!producer.id().equals(skill.userId())) {
            throw new AccessDeniedException("Only the skill producer can view accepted receivers");
        }

        // CORRECTION: Récupérer TOUS les échanges (ACCEPTED, COMPLETED, IN_PROGRESS, SCHEDULED) pour cette compétence
        List<String> validStatuses = List.of(
                ExchangeStatus.ACCEPTED.toString(),
                ExchangeStatus.COMPLETED.toString(),
                ExchangeStatus.IN_PROGRESS.toString(),
                ExchangeStatus.SCHEDULED.toString()
        );

        List<Exchange> exchanges = exchangeRepository.findBySkillIdAndStatusIn(skillId, validStatuses);
        log.info("Found {} exchanges for skill {} with valid statuses", exchanges.size(), skillId);

        // Collecter les IDs des receivers uniques
        Set<Long> receiverIds = exchanges.stream()
                .map(Exchange::getReceiverId)
                .collect(Collectors.toSet());

        // Récupérer les détails des receivers
        List<UserResponse> receivers = new ArrayList<>();
        for (Long receiverId : receiverIds) {
            try {
                UserResponse receiver = fetchUserById(receiverId, token);
                receivers.add(receiver);
            } catch (Exception e) {
                log.error("Failed to fetch user with ID {}: {}", receiverId, e.getMessage());
            }
        }

        log.info("Returning {} unique receivers for skill {}", receivers.size(), skillId);
        return receivers;
    }
    @Transactional(readOnly = true)
    public List<SkillResponse> getAcceptedSkillsForReceiver(Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        UserResponse receiver = getAuthenticatedUser(jwt);
        log.info("Fetching skills for receiver ID: {} with statuses: ACCEPTED, PENDING, COMPLETED, IN_PROGRESS", receiver.id());

        // Define the statuses we want to include
        List<String> targetStatuses = List.of(
                ExchangeStatus.ACCEPTED.toString(),

                ExchangeStatus.COMPLETED.toString(),
                ExchangeStatus.IN_PROGRESS.toString()
        );

        // Use optimized repository method for better performance
        List<Exchange> exchanges = exchangeRepository.findByReceiverIdAndStatusIn(receiver.id(), targetStatuses);

        if (exchanges.isEmpty()) {
            log.info("No exchanges found for receiver ID: {} with target statuses", receiver.id());
            return List.of();
        }

        log.info("Found {} exchanges for receiver ID: {}", exchanges.size(), receiver.id());

        return exchanges.stream()
                .map(exchange -> {
                    SkillResponse skill = fetchSkill(exchange.getSkillId());
                    if (skill == null) {
                        log.warn("Skill not found for exchange ID: {} with skill ID: {}", exchange.getId(), exchange.getSkillId());
                    }
                    return skill;
                })
                .filter(Objects::nonNull)
                .distinct() // Remove duplicates if same skill appears in multiple exchanges
                .collect(Collectors.toList());
    }
    public boolean isSessionCompletedForSkill(Integer skillId) {
        List<Exchange> exchanges = exchangeRepository.findBySkillId(skillId);

        // Vérifier si au moins un échange est en statut COMPLETED
        return exchanges.stream()
                .anyMatch(exchange -> ExchangeStatus.COMPLETED.toString().equals(exchange.getStatus()));
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
        if (isSessionCompletedForSkill(request.skillId())) {
            throw new InvalidExchangeException("Une session a déjà été complétée pour cette compétence. Impossible de créer un nouvel échange.");
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
            // CORRECTION: Utilisation de la nouvelle signature
            exchangeValidator.validateStatusTransition(exchange, ExchangeStatus.ACCEPTED.toString());

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
    @Transactional
    public void updateStatus(Integer exchangeId, String status, Jwt jwt) {
        Exchange exchange = getExchange(exchangeId);
        String token = "Bearer " + jwt.getTokenValue();

        // Récupérer les rôles de l'utilisateur
        List<String> roles = jwt.getClaimAsStringList("roles");
        boolean isServiceAccount = roles != null && roles.contains("SERVICE_ACCOUNT");

        // Valider l'utilisateur
        if (!isServiceAccount) {
            UserResponse user = getAuthenticatedUser(jwt);
            if (!user.id().equals(exchange.getProducerId())) {
                throw new AccessDeniedException("Only the producer can update the exchange status");
            }
        }

        // CORRECTION: Utilisation de la nouvelle signature
        exchangeValidator.validateStatusTransition(exchange, status);

        // Mettre à jour le statut
        exchange.setStatus(status);
        exchangeRepository.save(exchange);
        log.info("Exchange ID {} status updated to {}", exchangeId, status);

        // Envoyer les notifications seulement pour les comptes utilisateurs normaux
        if (!isServiceAccount) {
            SkillResponse skill = fetchSkill(exchange.getSkillId());
            UserResponse receiver = fetchUserById(exchange.getReceiverId(), token);
            UserResponse producer = fetchUserById(exchange.getProducerId(), token);

            sendStatusNotification(status, receiver, producer, skill, exchange);
        }
    }
    private void sendStatusNotification(String status, UserResponse receiver,
                                        UserResponse producer, SkillResponse skill,
                                        Exchange exchange) { // CORRECTION: Ajouter le paramètre Exchange
        switch (status) {
            case "ACCEPTED":
                notificationService.notifyRequestAccepted(receiver, producer, skill, exchange.getId());
                break;
            case "REJECTED":
                // CORRECTION: Utiliser exchange.getRejectionReason()
                notificationService.notifyRequestRejected(receiver, producer, skill, exchange.getRejectionReason(), exchange.getId());
                break;
            case "SCHEDULED":
                notificationService.notifySessionScheduled(receiver, producer, skill, exchange.getId());
                break;
            case "IN_PROGRESS":
                notificationService.notifySessionStarted(receiver, producer, skill, exchange.getId());
                break;
            case "COMPLETED":
                notificationService.notifySessionCompleted(receiver, producer, skill, exchange.getId());
                break;
            default:
                log.warn("No notification defined for status: {}", status);
        }
    }
    @Transactional(readOnly = true)
    public List<ExchangeResponse> getExchangesBySkillId(Integer skillId, Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        UserResponse user = getAuthenticatedUser(jwt);

        // Verify that the skill exists
        SkillResponse skill = fetchSkill(skillId);
        if (skill == null) {
            log.warn("Skill ID {} not found", skillId);
            throw new SkillNotFoundException("Skill not found");
        }

        // Verify that the user is either the producer or a receiver of an exchange for this skill
        Long userId = user.id();
        if (!userId.equals(skill.userId()) && !exchangeRepository.findBySkillIdAndReceiverId(skillId, userId).isPresent()) {
            log.warn("User ID {} is not authorized to view exchanges for skill ID {}", userId, skillId);
            throw new AccessDeniedException("User is not authorized to view exchanges for this skill");
        }

        // Fetch exchanges for the skill where the user is either the producer or receiver
        List<Exchange> exchanges = exchangeRepository.findBySkillIdAndUserId(skillId, userId);
        if (exchanges.isEmpty()) {
            log.info("No exchanges found for skill ID: {} and user ID: {}", skillId, userId);
            return List.of();
        }

        // Map exchanges to ExchangeResponse DTOs
        return exchanges.stream()
                .map(exchange -> {
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
            return null;
        } catch (FeignException.InternalServerError e) {
            log.error("Internal server error fetching skill with ID {}: {}", skillId, e.getMessage());
            return null;
        } catch (FeignException e) {
            log.error("Error fetching skill with ID {}: status={}, message={}", skillId, e.status(), e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Unexpected error fetching skill with ID {}: {}", skillId, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch skill", e);
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
    public List<SubscriberDetailResponse> getDetailedSubscribersForProducer(Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        UserResponse producer = getAuthenticatedUser(jwt);

        log.info("Fetching detailed subscribers for producer ID: {}", producer.id());

        // Récupérer tous les échanges valides pour ce producteur
        List<Exchange> exchanges = exchangeRepository.findAllSubscribersExchangesByProducerId(producer.id());

        if (exchanges.isEmpty()) {
            log.info("No subscribers found for producer ID: {}", producer.id());
            return List.of();
        }

        // Grouper les échanges par receiver ID
        Map<Long, List<Exchange>> exchangesByReceiver = exchanges.stream()
                .collect(Collectors.groupingBy(Exchange::getReceiverId));

        List<SubscriberDetailResponse> subscribers = new ArrayList<>();

        for (Map.Entry<Long, List<Exchange>> entry : exchangesByReceiver.entrySet()) {
            Long receiverId = entry.getKey();
            List<Exchange> receiverExchanges = entry.getValue();

            try {
                UserResponse receiver = fetchUserById(receiverId, token);
                if (receiver != null) {
                    // Créer les informations de compétences pour ce receiver
                    List<SkillSubscriptionInfo> skillsInfo = receiverExchanges.stream()
                            .map(exchange -> {
                                SkillResponse skill = fetchSkill(exchange.getSkillId());
                                return new SkillSubscriptionInfo(
                                        exchange.getSkillId(),
                                        skill != null ? skill.name() : "Skill indisponible",
                                        exchange.getStatus(),
                                        exchange.getCreatedAt()
                                );
                            })
                            .collect(Collectors.toList());

                    SubscriberDetailResponse subscriberDetail = new SubscriberDetailResponse(
                            receiver.id(),
                            receiver.keycloakId(),
                            receiver.username(),
                            receiver.email(),
                            receiver.firstName(),
                            receiver.lastName(),
                            receiver.pictureUrl(),
                            receiver.roles(),
                            skillsInfo
                    );

                    subscribers.add(subscriberDetail);
                }
            } catch (Exception e) {
                log.error("Failed to fetch detailed info for subscriber with ID {}: {}", receiverId, e.getMessage());
            }
        }

        log.info("Successfully retrieved {} unique detailed subscribers for producer ID: {}",
                subscribers.size(), producer.id());

        return subscribers;
    }

    // Version simplifiée pour juste la liste des utilisateurs uniques
    @Transactional(readOnly = true)
    public List<UserResponse> getAllSubscribersForProducer(Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        UserResponse producer = getAuthenticatedUser(jwt);

        log.info("Fetching all unique subscribers for producer ID: {}", producer.id());

        // Récupérer tous les échanges valides pour ce producteur
        List<Exchange> exchanges = exchangeRepository.findAllSubscribersExchangesByProducerId(producer.id());

        if (exchanges.isEmpty()) {
            log.info("No subscribers found for producer ID: {}", producer.id());
            return List.of();
        }

        log.info("Found {} exchanges for producer ID: {}", exchanges.size(), producer.id());

        // Collecter les IDs des receivers uniques
        Set<Long> uniqueReceiverIds = exchanges.stream()
                .map(Exchange::getReceiverId)
                .collect(Collectors.toSet());

        log.info("Found {} unique receivers for producer ID: {}", uniqueReceiverIds.size(), producer.id());

        // Récupérer les détails des receivers
        List<UserResponse> subscribers = new ArrayList<>();
        for (Long receiverId : uniqueReceiverIds) {
            try {
                UserResponse receiver = fetchUserById(receiverId, token);
                if (receiver != null) {
                    subscribers.add(receiver);
                }
            } catch (Exception e) {
                log.error("Failed to fetch subscriber with ID {}: {}", receiverId, e.getMessage());
            }
        }

        // Trier par nom pour une meilleure présentation
        subscribers.sort((u1, u2) -> {
            String name1 = (u1.firstName() != null ? u1.firstName() : "") + " " +
                    (u1.lastName() != null ? u1.lastName() : "");
            String name2 = (u2.firstName() != null ? u2.firstName() : "") + " " +
                    (u2.lastName() != null ? u2.lastName() : "");
            return name1.trim().compareToIgnoreCase(name2.trim());
        });

        log.info("Successfully retrieved {} unique subscribers for producer ID: {}",
                subscribers.size(), producer.id());

        return subscribers;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getPeerReceiversForReceiver(Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        UserResponse currentReceiver = getAuthenticatedUser(jwt);

        log.info("Fetching peer receivers for receiver ID: {}", currentReceiver.id());

        // ÉTAPE 1: Récupérer tous les skill IDs auxquels ce receiver est inscrit
        List<Integer> skillIds = exchangeRepository.findSkillIdsByReceiverId(currentReceiver.id());

        if (skillIds.isEmpty()) {
            log.info("Receiver ID {} is not subscribed to any skills", currentReceiver.id());
            return List.of();
        }

        log.info("Receiver ID {} is subscribed to {} skills: {}",
                currentReceiver.id(), skillIds.size(), skillIds);

        // ÉTAPE 2: Récupérer les IDs des autres receivers inscrits aux mêmes compétences
        List<Long> peerReceiverIds = exchangeRepository.findPeerReceiverIdsBySkillIds(skillIds, currentReceiver.id());

        if (peerReceiverIds.isEmpty()) {
            log.info("No peer receivers found for receiver ID: {}", currentReceiver.id());
            return List.of();
        }

        log.info("Found {} peer receivers for receiver ID: {}", peerReceiverIds.size(), currentReceiver.id());

        // ÉTAPE 3: Récupérer les détails des peer receivers
        List<UserResponse> peerReceivers = peerReceiverIds.stream()
                .map(receiverId -> {
                    try {
                        UserResponse receiver = fetchUserById(receiverId, token);
                        if (receiver != null && receiver.roles().contains("RECEIVER")) {
                            return receiver;
                        }
                        return null;
                    } catch (Exception e) {
                        log.error("Failed to fetch peer receiver with ID {}: {}", receiverId, e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .sorted((u1, u2) -> {
                    String name1 = (u1.firstName() != null ? u1.firstName() : "") + " " +
                            (u1.lastName() != null ? u1.lastName() : "");
                    String name2 = (u2.firstName() != null ? u2.firstName() : "") + " " +
                            (u2.lastName() != null ? u2.lastName() : "");
                    return name1.trim().compareToIgnoreCase(name2.trim());
                })
                .collect(Collectors.toList());

        log.info("Successfully retrieved {} peer receivers for receiver ID: {}",
                peerReceivers.size(), currentReceiver.id());

        return peerReceivers;
    }

    @Transactional(readOnly = true)
    public List<PeerReceiverDetailResponse> getDetailedPeerReceiversForReceiver(Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        UserResponse currentReceiver = getAuthenticatedUser(jwt);

        log.info("Fetching detailed peer receivers for receiver ID: {}", currentReceiver.id());

        // ÉTAPE 1: Récupérer tous les skill IDs auxquels ce receiver est inscrit
        List<Integer> skillIds = exchangeRepository.findSkillIdsByReceiverId(currentReceiver.id());

        if (skillIds.isEmpty()) {
            log.info("Receiver ID {} is not subscribed to any skills", currentReceiver.id());
            return List.of();
        }

        // ÉTAPE 2: Récupérer tous les échanges des peer receivers pour ces compétences
        List<Exchange> peerExchanges = exchangeRepository.findPeerReceiversBySkillIds(skillIds, currentReceiver.id());

        if (peerExchanges.isEmpty()) {
            log.info("No peer receivers found for receiver ID: {}", currentReceiver.id());
            return List.of();
        }

        // ÉTAPE 3: Grouper les échanges par receiver ID
        Map<Long, List<Exchange>> exchangesByReceiver = peerExchanges.stream()
                .collect(Collectors.groupingBy(Exchange::getReceiverId));

        List<PeerReceiverDetailResponse> detailedPeers = new ArrayList<>();

        for (Map.Entry<Long, List<Exchange>> entry : exchangesByReceiver.entrySet()) {
            Long receiverId = entry.getKey();
            List<Exchange> receiverExchanges = entry.getValue();

            try {
                UserResponse peerReceiver = fetchUserById(receiverId, token);
                if (peerReceiver != null && peerReceiver.roles().contains("RECEIVER")) {

                    // Créer les informations des compétences communes
                    List<CommonSkillInfo> commonSkills = receiverExchanges.stream()
                            .map(exchange -> {
                                SkillResponse skill = fetchSkill(exchange.getSkillId());
                                UserResponse producer = fetchUserById(exchange.getProducerId(), token);

                                return new CommonSkillInfo(
                                        exchange.getSkillId(),
                                        skill != null ? skill.name() : "Skill indisponible",
                                        producer != null ? producer.firstName() + " " + producer.lastName() : "Producteur indisponible",
                                        exchange.getStatus(),
                                        exchange.getCreatedAt()
                                );
                            })
                            .collect(Collectors.toList());

                    PeerReceiverDetailResponse peerDetail = new PeerReceiverDetailResponse(
                            peerReceiver.id(),
                            peerReceiver.keycloakId(),
                            peerReceiver.username(),
                            peerReceiver.email(),
                            peerReceiver.firstName(),
                            peerReceiver.lastName(),
                            peerReceiver.pictureUrl(),
                            peerReceiver.roles(),
                            commonSkills
                    );

                    detailedPeers.add(peerDetail);
                }
            } catch (Exception e) {
                log.error("Failed to fetch detailed info for peer receiver with ID {}: {}", receiverId, e.getMessage());
            }
        }

        // Trier par nombre de compétences communes (décroissant) puis par nom
        detailedPeers.sort((p1, p2) -> {
            int skillComparison = Integer.compare(p2.commonSkills().size(), p1.commonSkills().size());
            if (skillComparison != 0) {
                return skillComparison;
            }

            String name1 = (p1.firstName() != null ? p1.firstName() : "") + " " +
                    (p1.lastName() != null ? p1.lastName() : "");
            String name2 = (p2.firstName() != null ? p2.firstName() : "") + " " +
                    (p2.lastName() != null ? p2.lastName() : "");
            return name1.trim().compareToIgnoreCase(name2.trim());
        });

        log.info("Successfully retrieved {} detailed peer receivers for receiver ID: {}",
                detailedPeers.size(), currentReceiver.id());

        return detailedPeers;
    }

    // Méthode utilitaire pour obtenir un résumé des compétences communes
    @Transactional(readOnly = true)
    public Map<String, Object> getPeerReceiversSummary(Jwt jwt) {
        UserResponse currentReceiver = getAuthenticatedUser(jwt);

        List<Integer> skillIds = exchangeRepository.findSkillIdsByReceiverId(currentReceiver.id());
        List<Long> peerReceiverIds = exchangeRepository.findPeerReceiverIdsBySkillIds(skillIds, currentReceiver.id());

        Map<String, Object> summary = new HashMap<>();
        summary.put("subscribedSkillsCount", skillIds.size());
        summary.put("peerReceiversCount", peerReceiverIds.size());
        summary.put("subscribedSkillIds", skillIds);

        return summary;
    }

    @Transactional(readOnly = true)
    public List<SkillCommunityResponse> getSkillCommunitiesForReceiver(Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        UserResponse currentReceiver = getAuthenticatedUser(jwt);

        log.info("Fetching skill communities for receiver ID: {}", currentReceiver.id());

        // ÉTAPE 1: Récupérer tous les échanges du receiver
        List<Exchange> myExchanges = exchangeRepository.findReceiverExchangesForCommunity(currentReceiver.id());

        if (myExchanges.isEmpty()) {
            log.info("Receiver ID {} has no valid exchanges", currentReceiver.id());
            return List.of();
        }

        log.info("Found {} exchanges for receiver ID: {}", myExchanges.size(), currentReceiver.id());

        List<SkillCommunityResponse> communities = new ArrayList<>();

        // ÉTAPE 2: Pour chaque compétence, créer la communauté
        for (Exchange myExchange : myExchanges) {
            try {
                // Récupérer les détails de la compétence
                SkillResponse skill = fetchSkill(myExchange.getSkillId());
                if (skill == null) {
                    log.warn("Skill not found for ID: {}", myExchange.getSkillId());
                    continue;
                }

                // Récupérer le producteur
                UserResponse producer = fetchUserById(myExchange.getProducerId(), token);
                if (producer == null) {
                    log.warn("Producer not found for ID: {}", myExchange.getProducerId());
                    continue;
                }

                // Récupérer les autres receivers pour cette compétence
                List<Exchange> otherReceiverExchanges = exchangeRepository.findOtherReceiversForSkill(
                        myExchange.getSkillId(), currentReceiver.id());

                List<UserResponse> otherReceivers = otherReceiverExchanges.stream()
                        .map(exchange -> {
                            try {
                                UserResponse receiver = fetchUserById(exchange.getReceiverId(), token);
                                return (receiver != null && receiver.roles().contains("RECEIVER")) ? receiver : null;
                            } catch (Exception e) {
                                log.error("Failed to fetch receiver ID {}: {}", exchange.getReceiverId(), e.getMessage());
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .distinct() // Éviter les doublons si un receiver a plusieurs échanges
                        .sorted((u1, u2) -> {
                            String name1 = (u1.firstName() != null ? u1.firstName() : "") + " " +
                                    (u1.lastName() != null ? u1.lastName() : "");
                            String name2 = (u2.firstName() != null ? u2.firstName() : "") + " " +
                                    (u2.lastName() != null ? u2.lastName() : "");
                            return name1.trim().compareToIgnoreCase(name2.trim());
                        })
                        .collect(Collectors.toList());

                SkillCommunityResponse community = new SkillCommunityResponse(
                        skill.id(),
                        skill.name(),
                        skill.description(),
                        producer,
                        otherReceivers,
                        myExchange.getCreatedAt(),
                        myExchange.getStatus()
                );

                communities.add(community);

                log.info("Created community for skill '{}': 1 producer + {} other receivers",
                        skill.name(), otherReceivers.size());

            } catch (Exception e) {
                log.error("Failed to create community for exchange ID {}: {}", myExchange.getId(), e.getMessage());
            }
        }

        log.info("Successfully created {} skill communities for receiver ID: {}",
                communities.size(), currentReceiver.id());

        return communities;
    }

    @Transactional(readOnly = true)
    public List<CommunityMemberResponse> getAllCommunityMembersForReceiver(Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        UserResponse currentReceiver = getAuthenticatedUser(jwt);

        log.info("Fetching all community members for receiver ID: {}", currentReceiver.id());

        // Récupérer les compétences du receiver
        List<Integer> skillIds = exchangeRepository.findSkillIdsByReceiverId(currentReceiver.id());

        if (skillIds.isEmpty()) {
            return List.of();
        }

        // Récupérer tous les échanges pour ces compétences
        List<Exchange> allExchanges = exchangeRepository.findAllMembersForSkills(skillIds);

        // Grouper par userId (producer ou receiver)
        Map<Long, List<Exchange>> exchangesByUser = new HashMap<>();

        for (Exchange exchange : allExchanges) {
            // Ajouter le producer
            exchangesByUser.computeIfAbsent(exchange.getProducerId(), k -> new ArrayList<>()).add(exchange);

            // Ajouter le receiver (sauf le receiver courant)
            if (!exchange.getReceiverId().equals(currentReceiver.id())) {
                exchangesByUser.computeIfAbsent(exchange.getReceiverId(), k -> new ArrayList<>()).add(exchange);
            }
        }

        List<CommunityMemberResponse> members = new ArrayList<>();

        for (Map.Entry<Long, List<Exchange>> entry : exchangesByUser.entrySet()) {
            Long userId = entry.getKey();
            List<Exchange> userExchanges = entry.getValue();

            try {
                UserResponse user = fetchUserById(userId, token);
                if (user == null) continue;

                // Déterminer le type de membre et les compétences communes
                String memberType;
                List<Integer> commonSkillIds = new ArrayList<>();

                if (user.roles().contains("PRODUCER")) {
                    memberType = "PRODUCER";
                    // Pour un producer, récupérer les skills qu'il produit et auxquels le receiver est inscrit
                    commonSkillIds = userExchanges.stream()
                            .filter(e -> e.getProducerId().equals(userId))
                            .map(Exchange::getSkillId)
                            .distinct()
                            .collect(Collectors.toList());
                } else if (user.roles().contains("RECEIVER")) {
                    memberType = "RECEIVER";
                    // Pour un receiver, récupérer les skills en commun
                    commonSkillIds = userExchanges.stream()
                            .filter(e -> e.getReceiverId().equals(userId))
                            .map(Exchange::getSkillId)
                            .distinct()
                            .collect(Collectors.toList());
                } else {
                    continue; // Ignorer les utilisateurs sans rôle approprié
                }

                CommunityMemberResponse member = new CommunityMemberResponse(
                        user.id(),
                        user.keycloakId(),
                        user.username(),
                        user.email(),
                        user.firstName(),
                        user.lastName(),
                        user.pictureUrl(),
                        user.roles(),
                        memberType,
                        commonSkillIds
                );

                members.add(member);

            } catch (Exception e) {
                log.error("Failed to fetch community member with ID {}: {}", userId, e.getMessage());
            }
        }

        // Trier par type (PRODUCER d'abord) puis par nom
        members.sort((m1, m2) -> {
            int typeComparison = m1.memberType().compareTo(m2.memberType());
            if (typeComparison != 0) {
                return typeComparison;
            }

            String name1 = (m1.firstName() != null ? m1.firstName() : "") + " " +
                    (m1.lastName() != null ? m1.lastName() : "");
            String name2 = (m2.firstName() != null ? m2.firstName() : "") + " " +
                    (m2.lastName() != null ? m2.lastName() : "");
            return name1.trim().compareToIgnoreCase(name2.trim());
        });

        log.info("Successfully retrieved {} community members for receiver ID: {}",
                members.size(), currentReceiver.id());

        return members;
    }

    @Transactional(readOnly = true)
    public FullCommunityResponse getFullCommunityForReceiver(Jwt jwt) {
        // Récupérer les communautés par compétence
        List<SkillCommunityResponse> skillCommunities = getSkillCommunitiesForReceiver(jwt);

        // Récupérer tous les membres
        List<CommunityMemberResponse> allMembers = getAllCommunityMembersForReceiver(jwt);

        // Calculer les statistiques
        int totalSkills = skillCommunities.size();
        long totalProducers = allMembers.stream().filter(m -> "PRODUCER".equals(m.memberType())).count();
        long totalOtherReceivers = allMembers.stream().filter(m -> "RECEIVER".equals(m.memberType())).count();
        int totalMembers = allMembers.size();

        CommunityStats stats = new CommunityStats(
                totalSkills,
                (int) totalProducers,
                (int) totalOtherReceivers,
                totalMembers
        );

        return new FullCommunityResponse(skillCommunities, allMembers, stats);
    }

    @Transactional(readOnly = true)
    public SkillUsersResponse getSkillUsers(Integer skillId, Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        UserResponse currentUser = getAuthenticatedUser(jwt);

        log.info("Fetching users for skill ID: {} by user ID: {}", skillId, currentUser.id());

        // Vérifier que la compétence existe
        SkillResponse skill = fetchSkill(skillId);
        if (skill == null) {
            throw new SkillNotFoundException("Skill not found with ID: " + skillId);
        }

        // Récupérer le producteur de la compétence
        UserResponse skillProducer = fetchUserById(skill.userId(), token);
        if (skillProducer == null) {
            throw new UserNotFoundException("Producer not found for skill ID: " + skillId);
        }

        String currentUserRole = determineUserRole(currentUser, skillProducer, skillId);
        List<UserResponse> receivers = new ArrayList<>();

        if ("PRODUCER".equals(currentUserRole)) {
            // Si c'est le producteur, récupérer tous les receivers inscrits
            receivers = getReceiversForProducer(skillId, token);
            log.info("Producer view: found {} receivers for skill ID: {}", receivers.size(), skillId);

        } else if ("RECEIVER".equals(currentUserRole)) {
            // Si c'est un receiver, récupérer les autres receivers + vérifier qu'il est inscrit
            if (!exchangeRepository.isUserEnrolledInSkill(skillId, currentUser.id())) {
                throw new AccessDeniedException("You are not enrolled in this skill");
            }
            receivers = getOtherReceiversForReceiver(skillId, currentUser.id(), token);
            log.info("Receiver view: found {} other receivers for skill ID: {}", receivers.size(), skillId);

        } else {
            throw new AccessDeniedException("You don't have access to this skill's users");
        }

        // Calculer les statistiques
        SkillUsersStats stats = calculateSkillStats(skillId, receivers.size());

        SkillUsersResponse response = new SkillUsersResponse(
                skill.id(),
                skill.name(),
                skill.description(),
                skillProducer,
                receivers,
                stats,
                currentUserRole
        );

        log.info("Successfully retrieved {} users for skill '{}' (role: {})",
                receivers.size() + 1, skill.name(), currentUserRole);

        return response;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getSkillUsersSimple(Integer skillId, Jwt jwt) {
        SkillUsersResponse fullResponse = getSkillUsers(skillId, jwt);

        List<UserResponse> allUsers = new ArrayList<>();

        // Ajouter le producteur seulement si l'utilisateur courant est un receiver
        if ("RECEIVER".equals(fullResponse.currentUserRole())) {
            allUsers.add(fullResponse.skillProducer());
        }

        // Ajouter tous les receivers
        allUsers.addAll(fullResponse.receivers());

        return allUsers;
    }

    private String determineUserRole(UserResponse currentUser, UserResponse skillProducer, Integer skillId) {
        // Vérifier si c'est le producteur de cette compétence
        if (currentUser.id().equals(skillProducer.id()) && currentUser.roles().contains("PRODUCER")) {
            return "PRODUCER";
        }

        // Vérifier si c'est un receiver inscrit à cette compétence
        if (currentUser.roles().contains("RECEIVER")) {
            boolean isEnrolled = exchangeRepository.isUserEnrolledInSkill(skillId, currentUser.id());
            if (isEnrolled) {
                return "RECEIVER";
            }
        }

        // Aucun accès valide
        return "NONE";
    }

    private List<UserResponse> getReceiversForProducer(Integer skillId, String token) {
        List<Exchange> exchanges = exchangeRepository.findValidExchangesBySkillId(skillId);

        return exchanges.stream()
                .map(Exchange::getReceiverId)
                .distinct()
                .map(receiverId -> {
                    try {
                        UserResponse receiver = fetchUserById(receiverId, token);
                        return (receiver != null && receiver.roles().contains("RECEIVER")) ? receiver : null;
                    } catch (Exception e) {
                        log.error("Failed to fetch receiver ID {}: {}", receiverId, e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .sorted((u1, u2) -> {
                    String name1 = (u1.firstName() != null ? u1.firstName() : "") + " " +
                            (u1.lastName() != null ? u1.lastName() : "");
                    String name2 = (u2.firstName() != null ? u2.firstName() : "") + " " +
                            (u2.lastName() != null ? u2.lastName() : "");
                    return name1.trim().compareToIgnoreCase(name2.trim());
                })
                .collect(Collectors.toList());
    }

    private List<UserResponse> getOtherReceiversForReceiver(Integer skillId, Long currentUserId, String token) {
        List<Exchange> exchanges = exchangeRepository.findValidExchangesBySkillIdExcludingUser(skillId, currentUserId);

        return exchanges.stream()
                .map(Exchange::getReceiverId)
                .distinct()
                .map(receiverId -> {
                    try {
                        UserResponse receiver = fetchUserById(receiverId, token);
                        return (receiver != null && receiver.roles().contains("RECEIVER")) ? receiver : null;
                    } catch (Exception e) {
                        log.error("Failed to fetch receiver ID {}: {}", receiverId, e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .sorted((u1, u2) -> {
                    String name1 = (u1.firstName() != null ? u1.firstName() : "") + " " +
                            (u1.lastName() != null ? u1.lastName() : "");
                    String name2 = (u2.firstName() != null ? u2.firstName() : "") + " " +
                            (u2.lastName() != null ? u2.lastName() : "");
                    return name1.trim().compareToIgnoreCase(name2.trim());
                })
                .collect(Collectors.toList());
    }

    private SkillUsersStats calculateSkillStats(Integer skillId, int receiversCount) {
        // Récupérer les statistiques de statuts
        List<Object[]> statusStats = exchangeRepository.getStatusStatsForSkill(skillId);

        Map<String, Integer> statusBreakdown = statusStats.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Long) row[1]).intValue()
                ));

        int totalUsers = receiversCount + 1; // +1 pour le producteur

        return new SkillUsersStats(receiversCount, totalUsers, statusBreakdown);
    }

    // Méthode utilitaire pour vérifier l'accès à une compétence
    @Transactional(readOnly = true)
    public boolean hasAccessToSkill(Integer skillId, Jwt jwt) {
        try {
            UserResponse currentUser = getAuthenticatedUser(jwt);
            SkillResponse skill = fetchSkill(skillId);

            if (skill == null) return false;

            // Producteur de la compétence
            if (currentUser.id().equals(skill.userId()) && currentUser.roles().contains("PRODUCER")) {
                return true;
            }

            // Receiver inscrit à la compétence
            if (currentUser.roles().contains("RECEIVER")) {
                return exchangeRepository.isUserEnrolledInSkill(skillId, currentUser.id());
            }

            return false;
        } catch (Exception e) {
            log.error("Error checking access to skill {}: {}", skillId, e.getMessage());
            return false;
        }
    }



    // Ajouter ces méthodes dans ExchangeService.java

    /**
     * Récupère toutes les compétences de l'utilisateur connecté avec leurs utilisateurs
     */
    @Transactional(readOnly = true)
    public UserSkillsWithUsersResponse getAllUserSkillsWithUsers(Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        UserResponse currentUser = getAuthenticatedUser(jwt);

        log.info("Fetching all skills with users for user ID: {}", currentUser.id());

        if (currentUser.roles().contains("PRODUCER")) {
            return getProducerSkillsWithUsers(currentUser, token);
        } else if (currentUser.roles().contains("RECEIVER")) {
            return getReceiverSkillsWithUsers(currentUser, token);
        } else {
            throw new AccessDeniedException("User must have PRODUCER or RECEIVER role");
        }
    }

    /**
     * Logique pour un PRODUCER : récupère toutes ses compétences avec les receivers inscrits
     */
    private UserSkillsWithUsersResponse getProducerSkillsWithUsers(UserResponse producer, String token) {
        log.info("Fetching producer skills for producer ID: {}", producer.id());

        // Récupérer tous les skill IDs du producteur
        List<Integer> skillIds = exchangeRepository.findSkillIdsByProducerId(producer.id());

        if (skillIds.isEmpty()) {
            log.info("No skills found for producer ID: {}", producer.id());
            return new UserSkillsWithUsersResponse(producer, "PRODUCER", List.of(),
                    new UserSkillsStats(0, 0, 0, 0, Map.of()));
        }

        log.info("Found {} skills for producer ID: {}", skillIds.size(), producer.id());

        // Récupérer tous les exchanges pour ces compétences
        List<Exchange> allExchanges = exchangeRepository.findAllValidExchangesByProducerId(producer.id());

        // Grouper par skill ID
        Map<Integer, List<Exchange>> exchangesBySkill = allExchanges.stream()
                .collect(Collectors.groupingBy(Exchange::getSkillId));

        List<SkillWithUsersResponse> skillsWithUsers = new ArrayList<>();
        Set<Long> allUniqueReceivers = new HashSet<>();
        Map<String, Integer> globalStatusBreakdown = new HashMap<>();

        for (Integer skillId : skillIds) {
            try {
                SkillResponse skill = fetchSkill(skillId);
                if (skill == null) {
                    log.warn("Skill not found for ID: {}", skillId);
                    continue;
                }

                List<Exchange> skillExchanges = exchangesBySkill.getOrDefault(skillId, List.of());

                // Récupérer les receivers uniques pour cette compétence
                Set<Long> receiverIds = skillExchanges.stream()
                        .map(Exchange::getReceiverId)
                        .collect(Collectors.toSet());

                List<UserResponse> receivers = receiverIds.stream()
                        .map(receiverId -> {
                            try {
                                UserResponse receiver = fetchUserById(receiverId, token);
                                return (receiver != null && receiver.roles().contains("RECEIVER")) ? receiver : null;
                            } catch (Exception e) {
                                log.error("Failed to fetch receiver ID {}: {}", receiverId, e.getMessage());
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .sorted((u1, u2) -> {
                            String name1 = (u1.firstName() != null ? u1.firstName() : "") + " " +
                                    (u1.lastName() != null ? u1.lastName() : "");
                            String name2 = (u2.firstName() != null ? u2.firstName() : "") + " " +
                                    (u2.lastName() != null ? u2.lastName() : "");
                            return name1.trim().compareToIgnoreCase(name2.trim());
                        })
                        .collect(Collectors.toList());

                // Calculer les stats pour cette compétence
                Map<String, Integer> skillStatusBreakdown = skillExchanges.stream()
                        .collect(Collectors.groupingBy(
                                Exchange::getStatus,
                                Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
                        ));

                SkillUsersStats skillStats = new SkillUsersStats(
                        receivers.size(),
                        receivers.size() + 1, // +1 pour le producteur
                        skillStatusBreakdown
                );

                SkillWithUsersResponse skillWithUsers = new SkillWithUsersResponse(
                        skill.id(),
                        skill.name(),
                        skill.description(),
                        producer,
                        receivers,
                        skillStats,
                        "PRODUCER"
                );

                skillsWithUsers.add(skillWithUsers);

                // Ajouter aux statistiques globales
                allUniqueReceivers.addAll(receiverIds);
                skillStatusBreakdown.forEach((status, count) ->
                        globalStatusBreakdown.merge(status, count, Integer::sum));

                log.info("Processed skill '{}': {} receivers", skill.name(), receivers.size());

            } catch (Exception e) {
                log.error("Failed to process skill ID {}: {}", skillId, e.getMessage());
            }
        }

        UserSkillsStats globalStats = new UserSkillsStats(
                skillsWithUsers.size(),
                allUniqueReceivers.size() + 1, // +1 pour le producteur
                1, // le producteur lui-même
                allUniqueReceivers.size(),
                globalStatusBreakdown
        );

        log.info("Producer summary: {} skills, {} unique receivers",
                skillsWithUsers.size(), allUniqueReceivers.size());

        return new UserSkillsWithUsersResponse(producer, "PRODUCER", skillsWithUsers, globalStats);
    }

    /**
     * Logique pour un RECEIVER : récupère toutes ses compétences avec producteurs et autres receivers
     */
    private UserSkillsWithUsersResponse getReceiverSkillsWithUsers(UserResponse receiver, String token) {
        log.info("Fetching receiver skills for receiver ID: {}", receiver.id());

        // Récupérer tous les skill IDs du receiver
        List<Integer> skillIds = exchangeRepository.findSkillIdsByReceiverId(receiver.id());

        if (skillIds.isEmpty()) {
            log.info("No skills found for receiver ID: {}", receiver.id());
            return new UserSkillsWithUsersResponse(receiver, "RECEIVER", List.of(),
                    new UserSkillsStats(0, 0, 0, 0, Map.of()));
        }

        log.info("Found {} skills for receiver ID: {}", skillIds.size(), receiver.id());

        // Récupérer tous les exchanges du receiver
        List<Exchange> myExchanges = exchangeRepository.findAllValidExchangesByReceiverId(receiver.id());

        // Récupérer tous les autres receivers pour ces compétences
        List<Exchange> otherReceiversExchanges = exchangeRepository.findOtherReceiversForSkills(skillIds, receiver.id());

        // Grouper par skill ID
        Map<Integer, Exchange> myExchangesBySkill = myExchanges.stream()
                .collect(Collectors.toMap(Exchange::getSkillId, e -> e, (e1, e2) -> e1));

        Map<Integer, List<Exchange>> otherExchangesBySkill = otherReceiversExchanges.stream()
                .collect(Collectors.groupingBy(Exchange::getSkillId));

        List<SkillWithUsersResponse> skillsWithUsers = new ArrayList<>();
        Set<Long> allUniqueProducers = new HashSet<>();
        Set<Long> allUniqueOtherReceivers = new HashSet<>();
        Map<String, Integer> globalStatusBreakdown = new HashMap<>();

        for (Integer skillId : skillIds) {
            try {
                SkillResponse skill = fetchSkill(skillId);
                if (skill == null) {
                    log.warn("Skill not found for ID: {}", skillId);
                    continue;
                }

                // Récupérer le producteur de cette compétence
                UserResponse producer = fetchUserById(skill.userId(), token);
                if (producer == null) {
                    log.warn("Producer not found for skill ID: {}", skillId);
                    continue;
                }

                // Récupérer les autres receivers pour cette compétence
                List<Exchange> otherSkillExchanges = otherExchangesBySkill.getOrDefault(skillId, List.of());

                Set<Long> otherReceiverIds = otherSkillExchanges.stream()
                        .map(Exchange::getReceiverId)
                        .collect(Collectors.toSet());

                List<UserResponse> otherReceivers = otherReceiverIds.stream()
                        .map(receiverId -> {
                            try {
                                UserResponse otherReceiver = fetchUserById(receiverId, token);
                                return (otherReceiver != null && otherReceiver.roles().contains("RECEIVER")) ? otherReceiver : null;
                            } catch (Exception e) {
                                log.error("Failed to fetch receiver ID {}: {}", receiverId, e.getMessage());
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .sorted((u1, u2) -> {
                            String name1 = (u1.firstName() != null ? u1.firstName() : "") + " " +
                                    (u1.lastName() != null ? u1.lastName() : "");
                            String name2 = (u2.firstName() != null ? u2.firstName() : "") + " " +
                                    (u2.lastName() != null ? u2.lastName() : "");
                            return name1.trim().compareToIgnoreCase(name2.trim());
                        })
                        .collect(Collectors.toList());

                // Calculer les stats pour cette compétence
                Exchange myExchange = myExchangesBySkill.get(skillId);
                Map<String, Integer> skillStatusBreakdown = new HashMap<>();
                if (myExchange != null) {
                    skillStatusBreakdown.put(myExchange.getStatus(), 1);
                }

                // Ajouter les statuts des autres receivers
                otherSkillExchanges.stream()
                        .collect(Collectors.groupingBy(
                                Exchange::getStatus,
                                Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
                        ))
                        .forEach((status, count) ->
                                skillStatusBreakdown.merge(status, count, Integer::sum));

                SkillUsersStats skillStats = new SkillUsersStats(
                        otherReceivers.size() + 1, // +1 pour le receiver courant
                        otherReceivers.size() + 2, // +1 receiver courant +1 producteur
                        skillStatusBreakdown
                );

                SkillWithUsersResponse skillWithUsers = new SkillWithUsersResponse(
                        skill.id(),
                        skill.name(),
                        skill.description(),
                        producer,
                        otherReceivers,
                        skillStats,
                        "RECEIVER"
                );

                skillsWithUsers.add(skillWithUsers);

                // Ajouter aux statistiques globales
                allUniqueProducers.add(producer.id());
                allUniqueOtherReceivers.addAll(otherReceiverIds);
                skillStatusBreakdown.forEach((status, count) ->
                        globalStatusBreakdown.merge(status, count, Integer::sum));

                log.info("Processed skill '{}': 1 producer, {} other receivers",
                        skill.name(), otherReceivers.size());

            } catch (Exception e) {
                log.error("Failed to process skill ID {}: {}", skillId, e.getMessage());
            }
        }

        UserSkillsStats globalStats = new UserSkillsStats(
                skillsWithUsers.size(),
                allUniqueProducers.size() + allUniqueOtherReceivers.size() + 1, // +1 pour le receiver courant
                allUniqueProducers.size(),
                allUniqueOtherReceivers.size() + 1, // +1 pour le receiver courant
                globalStatusBreakdown
        );

        log.info("Receiver summary: {} skills, {} unique producers, {} other unique receivers",
                skillsWithUsers.size(), allUniqueProducers.size(), allUniqueOtherReceivers.size());

        return new UserSkillsWithUsersResponse(receiver, "RECEIVER", skillsWithUsers, globalStats);
    }

    /**
     * Version simplifiée qui retourne juste les utilisateurs pour toutes les compétences
     */
    @Transactional(readOnly = true)
    public List<UserResponse> getAllSkillUsersSimple(Jwt jwt) {
        UserSkillsWithUsersResponse fullResponse = getAllUserSkillsWithUsers(jwt);

        Set<UserResponse> allUniqueUsers = new HashSet<>();

        for (SkillWithUsersResponse skill : fullResponse.skills()) {
            // Ajouter le producteur si l'utilisateur courant est un receiver
            if ("RECEIVER".equals(fullResponse.userPrimaryRole())) {
                allUniqueUsers.add(skill.skillProducer());
            }

            // Ajouter tous les receivers
            allUniqueUsers.addAll(skill.receivers());
        }

        return allUniqueUsers.stream()
                .sorted((u1, u2) -> {
                    String name1 = (u1.firstName() != null ? u1.firstName() : "") + " " +
                            (u1.lastName() != null ? u1.lastName() : "");
                    String name2 = (u2.firstName() != null ? u2.firstName() : "") + " " +
                            (u2.lastName() != null ? u2.lastName() : "");
                    return name1.trim().compareToIgnoreCase(name2.trim());
                })
                .collect(Collectors.toList());
    }
}