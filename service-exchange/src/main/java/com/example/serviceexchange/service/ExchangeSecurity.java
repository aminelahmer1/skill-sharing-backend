package com.example.serviceexchange.service;

import com.example.serviceexchange.dto.ExchangeRequest;
import com.example.serviceexchange.dto.UserResponse;
import com.example.serviceexchange.entity.Exchange;
import com.example.serviceexchange.exception.ExchangeNotFoundException;
import com.example.serviceexchange.exception.InvalidExchangeException;
import com.example.serviceexchange.repository.ExchangeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
/*
@Component
@RequiredArgsConstructor
public class ExchangeSecurity {

    private final ExchangeRepository exchangeRepository;

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

    public void validateExchangeCreation(UserResponse producer, UserResponse receiver, ExchangeRequest request) {
        if (!"PRODUCER".equals(producer.role())) {
            throw new InvalidExchangeException("Producer must have PRODUCER role");
        }

        if (!"RECEIVER".equals(receiver.role())) {
            throw new InvalidExchangeException("Receiver must have RECEIVER role");
        }

        if (request.producerId().equals(request.receiverId())) {
            throw new InvalidExchangeException("Producer and Receiver cannot be the same user");
        }
    }
}*/