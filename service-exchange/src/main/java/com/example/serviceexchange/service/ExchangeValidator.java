package com.example.serviceexchange.service;

import com.example.serviceexchange.dto.ExchangeRequest;
import com.example.serviceexchange.dto.SkillResponse;
import com.example.serviceexchange.entity.Exchange;
import com.example.serviceexchange.entity.ExchangeStatus;
import com.example.serviceexchange.exception.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class ExchangeValidator {

    public void validateExchangeCreation(ExchangeRequest request, Jwt jwt) {
        if (!jwt.getSubject().equals(request.receiverId().toString())) {
            throw new AccessDeniedException("You can only create exchanges as yourself");
        }

        if (request.providerId().equals(request.receiverId())) {
            throw new InvalidExchangeException("Provider and Receiver cannot be the same user");
        }
    }

    public void validateSkill(SkillResponse skill, Long providerId) {
        if (!providerId.equals(skill.userId())) {
            throw new InvalidExchangeException("Skill does not belong to the provider");
        }
    }

    public void validateStatusUpdate(Exchange exchange, String newStatus, Jwt jwt) {
        if (!ExchangeStatus.isValid(newStatus)) {
            throw new InvalidStatusException("Invalid status: " + newStatus);
        }

        String userId = jwt.getSubject();
        boolean isProvider = userId.equals(exchange.getProviderId().toString());
        boolean isReceiver = userId.equals(exchange.getReceiverId().toString());

        switch (exchange.getStatus()) {
            case ExchangeStatus.PENDING:
                if ((ExchangeStatus.ACCEPTED.equals(newStatus) ||
                        ExchangeStatus.REJECTED.equals(newStatus)) && !isProvider) {
                    throw new AccessDeniedException("Only provider can accept/reject pending exchanges");
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
}