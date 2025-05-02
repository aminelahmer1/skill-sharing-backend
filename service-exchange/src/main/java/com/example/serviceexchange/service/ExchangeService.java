package com.example.serviceexchange.service;

import com.example.serviceexchange.FeignClient.SkillServiceClient;
import com.example.serviceexchange.FeignClient.UserServiceClient;
import com.example.serviceexchange.dto.*;
import com.example.serviceexchange.entity.Exchange;
import com.example.serviceexchange.entity.ExchangeStatus;
import com.example.serviceexchange.exception.ExchangeNotFoundException;
import com.example.serviceexchange.repository.ExchangeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExchangeService {

    private final ExchangeRepository exchangeRepository;
    private final UserServiceClient userServiceClient;
    private final SkillServiceClient skillServiceClient;
    private final ExchangeValidator exchangeValidator;

    private UserResponse getAuthenticatedUser(Jwt jwt) {
        return userServiceClient.getUserByKeycloakId(
                jwt.getSubject(),
                "Bearer " + jwt.getTokenValue()
        );
    }

    @Transactional
    public ExchangeResponse createExchange(ExchangeRequest request, Jwt jwt) {
        UserResponse receiver = getAuthenticatedUser(jwt);

        if (!receiver.roles().contains("RECEIVER")) {
            throw new AccessDeniedException("Only receivers can create exchanges");
        }

        if (!receiver.id().equals(request.receiverId())) {
            throw new AccessDeniedException("You can only create exchanges for yourself");
        }

        UserResponse producer = userServiceClient.getUserById(
                request.producerId(),
                "Bearer " + jwt.getTokenValue()
        );

        exchangeValidator.validateExchangeCreation(request, producer, receiver);

        SkillResponse skill = skillServiceClient.getSkillById(request.skillId());
        exchangeValidator.validateSkill(skill, producer.id());

        Exchange exchange = Exchange.builder()
                .producerId(request.producerId())
                .receiverId(request.receiverId())
                .skillId(request.skillId())
                .status(ExchangeStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        return toResponse(exchangeRepository.save(exchange));
    }

    @Transactional
    public ExchangeResponse updateStatus(Integer exchangeId, String newStatus, Jwt jwt) {
        UserResponse user = getAuthenticatedUser(jwt);
        Exchange exchange = exchangeRepository.findById(exchangeId)
                .orElseThrow(() -> new ExchangeNotFoundException("Exchange not found"));

        exchangeValidator.validateStatusUpdate(exchange, newStatus, jwt);
        exchange.setStatus(newStatus);

        return toResponse(exchangeRepository.save(exchange));
    }

    @Transactional
    public ExchangeResponse rateExchange(Integer exchangeId, Integer rating, Jwt jwt) {
        Exchange exchange = exchangeRepository.findById(exchangeId)
                .orElseThrow(() -> new ExchangeNotFoundException("Exchange not found"));

        exchangeValidator.validateRating(exchange, rating, jwt);
        exchange.setProducerRating(rating);
        exchange.setStatus(ExchangeStatus.COMPLETED);

        return toResponse(exchangeRepository.save(exchange));
    }

    public List<ExchangeResponse> getUserExchanges(Jwt jwt) {
        UserResponse user = getAuthenticatedUser(jwt);
        return exchangeRepository.findByProducerIdOrReceiverId(user.id(), user.id())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private ExchangeResponse toResponse(Exchange exchange) {
        return new ExchangeResponse(
                exchange.getId(),
                exchange.getProducerId(),
                exchange.getReceiverId(),
                exchange.getSkillId(),
                exchange.getStatus(),
                exchange.getCreatedAt(),
                exchange.getProducerRating()
        );
    }
}