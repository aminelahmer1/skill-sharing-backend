package com.example.serviceexchange.service;

import com.example.serviceexchange.FeignClient.SkillServiceClient;
import com.example.serviceexchange.FeignClient.UserServiceClient;
import com.example.serviceexchange.dto.ExchangeRequest;
import com.example.serviceexchange.dto.ExchangeResponse;
import com.example.serviceexchange.dto.SkillResponse;
import com.example.serviceexchange.entity.Exchange;
import com.example.serviceexchange.entity.ExchangeStatus;
import com.example.serviceexchange.exception.ExchangeNotFoundException;
import com.example.serviceexchange.repository.ExchangeRepository;
import lombok.RequiredArgsConstructor;
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

    @Transactional
    public ExchangeResponse createExchange(ExchangeRequest request, Jwt jwt) {
        exchangeValidator.validateExchangeCreation(request, jwt);

        SkillResponse skill = skillServiceClient.getSkillById(request.skillId());
        exchangeValidator.validateSkill(skill, request.producerId());

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
        Exchange exchange = getExchangeById(exchangeId);
        exchangeValidator.validateStatusUpdate(exchange, newStatus, jwt);

        exchange.setStatus(newStatus);
        return toResponse(exchangeRepository.save(exchange));
    }

    @Transactional
    public ExchangeResponse rateExchange(Integer exchangeId, Integer rating, Jwt jwt) {
        Exchange exchange = getExchangeById(exchangeId);
        exchangeValidator.validateRating(exchange, rating, jwt);

        exchange.setProducerRating(rating);
        exchange.setStatus(ExchangeStatus.COMPLETED);
        return toResponse(exchangeRepository.save(exchange));
    }

    public List<ExchangeResponse> getUserExchanges(Jwt jwt) {
        Long userId = Long.parseLong(jwt.getSubject());
        return exchangeRepository.findByProducerIdOrReceiverId(userId, userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private Exchange getExchangeById(Integer id) {
        return exchangeRepository.findById(id)
                .orElseThrow(() -> new ExchangeNotFoundException("Exchange not found with id: " + id));
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