package com.example.serviceexchange.service;

import com.example.serviceexchange.dto.ExchangeRequest;
import com.example.serviceexchange.dto.UserResponse;
import com.example.serviceexchange.entity.Exchange;
import com.example.serviceexchange.entity.ExchangeStatus;
import com.example.serviceexchange.exception.InvalidExchangeException;
import com.example.serviceexchange.exception.InvalidStatusTransitionException;
import com.example.serviceexchange.repository.ExchangeRepository;
import org.springframework.stereotype.Component;

@Component
public class ExchangeValidator {
    private final ExchangeRepository exchangeRepository;

    public ExchangeValidator(ExchangeRepository exchangeRepository) {
        this.exchangeRepository = exchangeRepository;
    }
    public void validateExchangeCreation(ExchangeRequest request, UserResponse producer, UserResponse receiver) {
        if (request.producerId().equals(request.receiverId())) {
            throw new InvalidExchangeException("Producer and receiver cannot be the same user");
        }

        boolean exists = exchangeRepository.existsByProducerIdAndReceiverIdAndSkillId(
                request.producerId(),
                request.receiverId(),
                request.skillId()
        );

        if (exists) {
            throw new InvalidExchangeException("You have already requested this skill");
        }
    }
    public void validateStatusTransition(Exchange exchange, String newStatus) {
        // Valide que le nouveau statut est un ExchangeStatus valide
        try {
            ExchangeStatus.valueOf(newStatus);
        } catch (IllegalArgumentException e) {
            throw new InvalidStatusTransitionException("Invalid status: " + newStatus);
        }

        // Valide les transitions d'état en fonction du statut actuel
        switch (exchange.getStatus()) {
            case "PENDING":
                if (!"ACCEPTED".equals(newStatus) && !"REJECTED".equals(newStatus)) {
                    throw new InvalidStatusTransitionException("PENDING exchange can only transition to ACCEPTED or REJECTED");
                }
                break;
            case "ACCEPTED":
                if (!"SCHEDULED".equals(newStatus) && !"IN_PROGRESS".equals(newStatus)) {
                    throw new InvalidStatusTransitionException("ACCEPTED exchange can only transition to SCHEDULED or IN_PROGRESS");
                }
                break;
            case "SCHEDULED":
                if (!"IN_PROGRESS".equals(newStatus)) {
                    throw new InvalidStatusTransitionException("SCHEDULED exchange can only transition to IN_PROGRESS");
                }
                break;
            case "IN_PROGRESS":
                if (!"COMPLETED".equals(newStatus)) {
                    throw new InvalidStatusTransitionException("IN_PROGRESS exchange can only transition to COMPLETED");
                }
                break;
            case "REJECTED":
            case "COMPLETED":
            case "CANCELLED":
                throw new InvalidStatusTransitionException("Cannot change status from " + exchange.getStatus());
            default:
                throw new InvalidStatusTransitionException("Invalid current status: " + exchange.getStatus());
        }
    }
}