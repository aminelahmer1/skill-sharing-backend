package com.example.serviceexchange.service;

import com.example.serviceexchange.dto.ExchangeRequest;
import com.example.serviceexchange.dto.SkillResponse;
import com.example.serviceexchange.dto.UserResponse;
import com.example.serviceexchange.entity.Exchange;
import com.example.serviceexchange.entity.ExchangeStatus;
import com.example.serviceexchange.exception.*;
import com.example.serviceexchange.repository.ExchangeRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class ExchangeValidator {
private ExchangeRepository exchangeRepository;
    public void validateExchangeCreation(ExchangeRequest request, UserResponse producer, UserResponse receiver) {
        if (!producer.roles().contains("PRODUCER")) {
            throw new InvalidExchangeException("The producer must have PRODUCER role");
        }

        if (!receiver.roles().contains("RECEIVER")) {
            throw new InvalidExchangeException("The receiver must have RECEIVER role");
        }

        if (request.producerId().equals(request.receiverId())) {
            throw new InvalidExchangeException("Producer and receiver cannot be the same user");
        }
    }

    public void validateSkill(SkillResponse skill, Long producerId) {
        if (!producerId.equals(skill.userId())) {
            throw new InvalidExchangeException("Skill does not belong to the producer");
        }
    }

    public void validateStatusUpdate(Exchange exchange, String newStatus, Jwt jwt) {
        if (!ExchangeStatus.isValid(newStatus)) {
            throw new InvalidStatusException("Invalid status: " + newStatus);
        }

        String userId = jwt.getSubject();
        boolean isProducer = userId.equals(exchange.getProducerId().toString());
        boolean isReceiver = userId.equals(exchange.getReceiverId().toString());

        switch (exchange.getStatus()) {
            case ExchangeStatus.PENDING:
                if ((ExchangeStatus.ACCEPTED.equals(newStatus) ||
                        ExchangeStatus.REJECTED.equals(newStatus)) && !isProducer) {
                    throw new AccessDeniedException("Only producer can accept/reject pending exchanges");
                }
                break;
            case ExchangeStatus.ACCEPTED:
                if (ExchangeStatus.COMPLETED.equals(newStatus) && !isReceiver) {
                    throw new AccessDeniedException("Only receiver can complete accepted exchanges");
                }
                break;
            default:
                throw new InvalidStatusTransitionException("Cannot change status from " + exchange.getStatus());
        }
    }

    public void validateRating(Exchange exchange, Integer rating, Jwt jwt) {
        if (rating == null || rating < 1 || rating > 5) {
            throw new InvalidRatingException("Rating must be between 1 and 5");
        }

        if (!jwt.getSubject().equals(exchange.getReceiverId().toString())) {
            throw new AccessDeniedException("Only receiver can rate exchanges");
        }

        if (!ExchangeStatus.ACCEPTED.equals(exchange.getStatus())) {
            throw new InvalidStatusException("Only accepted exchanges can be rated");
        }
    }

    // Méthodes pour les annotations de sécurité
    public boolean isExchangeParticipant(Integer exchangeId, Jwt jwt) {
        Exchange exchange = exchangeRepository.findById(exchangeId)
                .orElseThrow(() -> new ExchangeNotFoundException("Exchange not found"));

        String userId = jwt.getSubject();
        return userId.equals(exchange.getProducerId().toString()) ||
                userId.equals(exchange.getReceiverId().toString());
    }

    public boolean isReceiver(Integer exchangeId, Jwt jwt) {
        Exchange exchange = exchangeRepository.findById(exchangeId)
                .orElseThrow(() -> new ExchangeNotFoundException("Exchange not found"));

        return jwt.getSubject().equals(exchange.getReceiverId().toString());
    }
}