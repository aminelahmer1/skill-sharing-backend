package com.example.serviceexchange.service;

import com.example.serviceexchange.dto.ExchangeRequest;
import com.example.serviceexchange.dto.UserResponse;
import com.example.serviceexchange.entity.Exchange;
import com.example.serviceexchange.entity.ExchangeStatus;
import com.example.serviceexchange.exception.*;
import com.example.serviceexchange.repository.ExchangeRepository;
import com.sun.jdi.request.DuplicateRequestException;
import org.springframework.security.access.AccessDeniedException;
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

        // Vérifier si une demande existe déjà
        boolean exists = exchangeRepository.existsByProducerIdAndReceiverIdAndSkillId(
                request.producerId(),
                request.receiverId(),
                request.skillId()
        );

        if (exists) {
            throw new DuplicateRequestException("You have already requested this skill");
        }
    }

    public void validateStatusTransition(Exchange exchange, String newStatus, String userId) {
        boolean isProducer = userId.equals(exchange.getProducerId().toString());

        switch (exchange.getStatus()) {
            case ExchangeStatus.PENDING:
                if (!ExchangeStatus.ACCEPTED.equals(newStatus) &&
                        !ExchangeStatus.REJECTED.equals(newStatus) &&
                        !isProducer) {
                    throw new AccessDeniedException("Only producer can accept/reject pending exchanges");
                }
                break;
            case ExchangeStatus.ACCEPTED:
                if (!ExchangeStatus.IN_PROGRESS.equals(newStatus) && !isProducer) {
                    throw new AccessDeniedException("Only producer can start the session");
                }
                break;
            case ExchangeStatus.IN_PROGRESS:
                if (!ExchangeStatus.COMPLETED.equals(newStatus) && !isProducer) {
                    throw new AccessDeniedException("Only producer can complete the session");
                }
                break;
            default:
                throw new InvalidStatusTransitionException("Cannot change status from " + exchange.getStatus());
        }
    }
}