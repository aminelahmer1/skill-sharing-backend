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

    @Transactional
    public ExchangeResponse createExchange(ExchangeRequest request, Jwt jwt) {
        String receiverToken = "Bearer " + jwt.getTokenValue();
        log.info("Creating exchange with request: {}", request);

        // Validation des participants
        UserResponse receiver = validateReceiver(request, jwt, receiverToken);
        UserResponse producer = validateProducer(request, receiverToken);
        SkillResponse skill = validateSkill(request, producer);

        // Vérification de la capacité
        if (skill.nbInscrits() >= skill.availableQuantity()) {
            throw new CapacityExceededException("No available slots for this skill");
        }

        // Validation de l'échange
        exchangeValidator.validateExchangeCreation(request, producer, receiver);

        // Création de l'échange
        Exchange exchange = Exchange.builder()
                .producerId(producer.id())
                .receiverId(receiver.id())
                .skillId(skill.id())
                .status(ExchangeStatus.PENDING)
                .streamingDate(parseStreamingDateTime(skill))
                .build();

        Exchange savedExchange = exchangeRepository.save(exchange);

        // Incrémenter nbInscrits avec le token du producer
        try {
            String producerToken = getProducerToken(producer.id(), receiverToken);
            skillServiceClient.incrementInscrits(skill.id(), producerToken);
            log.info("Incremented nbInscrits for skill ID: {}", skill.id());
        } catch (Exception e) {
            log.error("Failed to increment nbInscrits for skill ID: {}. Rolling back exchange creation.", skill.id(), e);
            throw new RuntimeException("Failed to increment skill registrations", e);
        }

        // Notification au producteur
        notificationService.notifyNewRequest(producer, receiver, skill);

        return toResponse(savedExchange);
    }

    @Transactional
    public ExchangeResponse acceptExchange(Integer exchangeId, Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        Exchange exchange = getExchange(exchangeId);
        validateProducerAction(exchange, jwt, token);

        // Vérifier la capacité
        SkillResponse skill = getSkill(exchange.getSkillId());
        if (skill.nbInscrits() >= skill.availableQuantity()) {
            throw new CapacityExceededException("No available slots for this skill");
        }

        exchange.setStatus(ExchangeStatus.ACCEPTED);
        Exchange updatedExchange = exchangeRepository.save(exchange);

        // Notification
        UserResponse producer = getUserById(exchange.getProducerId(), token);
        UserResponse receiver = getUserById(exchange.getReceiverId(), token);
        notificationService.notifyRequestAccepted(receiver, producer, skill);

        return toResponse(updatedExchange);
    }

    @Transactional
    public ExchangeResponse rejectExchange(Integer exchangeId, String reason, Jwt jwt) {
        String producerToken = "Bearer " + jwt.getTokenValue();
        Exchange exchange = getExchange(exchangeId);
        validateProducerAction(exchange, jwt, producerToken);

        exchange.setStatus(ExchangeStatus.REJECTED);
        Exchange updatedExchange = exchangeRepository.save(exchange);

        // Décrémenter nbInscrits avec le token du producer
        try {
            String token = getProducerToken(exchange.getProducerId(), producerToken);
            skillServiceClient.decrementInscrits(exchange.getSkillId(), token);
            log.info("Decremented nbInscrits for skill ID: {}", exchange.getSkillId());
        } catch (Exception e) {
            log.error("Failed to decrement nbInscrits for skill ID: {}. Proceeding with rejection.", exchange.getSkillId(), e);
            // Ne pas annuler le rejet en cas d'échec de décrémentation
        }

        // Notification
        UserResponse producer = getUserById(exchange.getProducerId(), producerToken);
        UserResponse receiver = getUserById(exchange.getReceiverId(), producerToken);
        SkillResponse skill = getSkill(exchange.getSkillId());
        notificationService.notifyRequestRejected(receiver, producer, skill, reason);

        return toResponse(updatedExchange);
    }

    @Transactional
    public void startSession(Integer skillId, Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        UserResponse user = getUserByKeycloakId(jwt.getSubject(), token);
        SkillResponse skill = getSkill(skillId);

        // Vérification que l'utilisateur est le producteur
        if (!user.id().equals(skill.userId())) {
            throw new AccessDeniedException("Only the producer can start the session");
        }

        List<Exchange> exchanges = exchangeRepository.findBySkillIdAndStatus(skillId, ExchangeStatus.ACCEPTED);

        if (exchanges.isEmpty()) {
            throw new NoParticipantsException("No accepted participants for this skill");
        }

        exchanges.forEach(exchange -> {
            exchange.setStatus(ExchangeStatus.IN_PROGRESS);
            exchangeRepository.save(exchange);

            UserResponse receiver = getUserById(exchange.getReceiverId(), token);
            notificationService.notifySessionStarted(receiver, user, skillId);
        });
    }

    @Transactional
    public void completeSession(Integer skillId, Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        UserResponse user = getUserByKeycloakId(jwt.getSubject(), token);
        SkillResponse skill = getSkill(skillId);

        // Vérification que l'utilisateur est le producteur
        if (!user.id().equals(skill.userId())) {
            throw new AccessDeniedException("Only the producer can complete the session");
        }

        List<Exchange> exchanges = exchangeRepository.findBySkillIdAndStatus(skillId, ExchangeStatus.IN_PROGRESS);

        if (exchanges.isEmpty()) {
            throw new NoParticipantsException("No in-progress participants for this skill");
        }

        exchanges.forEach(exchange -> {
            exchange.setStatus(ExchangeStatus.COMPLETED);
            exchangeRepository.save(exchange);

            UserResponse receiver = getUserById(exchange.getReceiverId(), token);
            notificationService.notifySessionCompleted(receiver, user, skillId);
        });
    }

    public List<ExchangeResponse> getUserExchanges(Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        UserResponse user = getUserByKeycloakId(jwt.getSubject(), token);
        return exchangeRepository.findUserExchanges(user.id()).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private Exchange getExchange(Integer exchangeId) {
        return exchangeRepository.findById(exchangeId)
                .orElseThrow(() -> new ExchangeNotFoundException("Exchange not found"));
    }

    private UserResponse getUserById(Long userId, String token) {
        log.info("Fetching user with ID: {} using token", userId);
        UserResponse userResponse = userServiceClient.getUserById(userId, token);
        log.info("Received user response: {}", userResponse);
        return userResponse;
    }

    private UserResponse getUserByKeycloakId(String keycloakId, String token) {
        return userServiceClient.getUserByKeycloakId(keycloakId, token);
    }

    private SkillResponse getSkill(Integer skillId) {
        return skillServiceClient.getSkillById(skillId);
    }

    private LocalDateTime parseStreamingDateTime(SkillResponse skill) {
        return LocalDateTime.parse(skill.streamingDate() + "T" + skill.streamingTime());
    }

    private void validateProducerAction(Exchange exchange, Jwt jwt, String token) {
        UserResponse user = getUserByKeycloakId(jwt.getSubject(), token);
        if (!user.id().equals(exchange.getProducerId())) {
            throw new AccessDeniedException("Only the producer can perform this action");
        }
    }

    private UserResponse validateReceiver(ExchangeRequest request, Jwt jwt, String token) {
        UserResponse receiver = getUserByKeycloakId(jwt.getSubject(), token);
        if (!receiver.roles().contains("RECEIVER")) {
            throw new AccessDeniedException("Only receivers can create exchanges");
        }
        if (!receiver.id().equals(request.receiverId())) {
            throw new AccessDeniedException("You can only create exchanges for yourself");
        }
        return receiver;
    }

    private UserResponse validateProducer(ExchangeRequest request, String token) {
        UserResponse producer = getUserById(request.producerId(), token);
        if (!producer.roles().contains("PRODUCER")) {
            throw new InvalidExchangeException("The producer must have PRODUCER role");
        }
        return producer;
    }

    private SkillResponse validateSkill(ExchangeRequest request, UserResponse producer) {
        SkillResponse skill = getSkill(request.skillId());
        if (!producer.id().equals(skill.userId())) {
            throw new InvalidExchangeException("Skill does not belong to the producer");
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
            UserResponse producer = userServiceClient.getUserById(producerId, fallbackToken);
            String keycloakId = producer.keycloakId();
            if (producer.roles().contains("PRODUCER")) {
                log.info("Using fallback token for producer ID: {}", producerId);
                return fallbackToken;
            } else {
                throw new RuntimeException("Producer token not available");
            }
        } catch (Exception e) {
            log.error("Failed to obtain producer token for producer ID: {}", producerId, e);
            throw new RuntimeException("Unable to obtain producer token", e);
        }
    }
}