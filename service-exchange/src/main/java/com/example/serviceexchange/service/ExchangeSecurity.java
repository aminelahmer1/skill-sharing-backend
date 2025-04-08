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

@Component
@RequiredArgsConstructor
public class ExchangeSecurity {

    private final ExchangeRepository exchangeRepository;

    public boolean isExchangeParticipant(Integer exchangeId, Jwt jwt) {
        Exchange exchange = exchangeRepository.findById(exchangeId)
                .orElseThrow(() -> new ExchangeNotFoundException("Exchange not found"));

        String userId = jwt.getSubject();
        return userId.equals(exchange.getProviderId().toString()) ||
                userId.equals(exchange.getReceiverId().toString());
    }

    public boolean isReceiver(Integer exchangeId, Jwt jwt) {
        Exchange exchange = exchangeRepository.findById(exchangeId)
                .orElseThrow(() -> new ExchangeNotFoundException("Exchange not found"));

        return jwt.getSubject().equals(exchange.getReceiverId().toString());
    }

    public void validateExchangeCreation(UserResponse provider, UserResponse receiver, ExchangeRequest request) {
        if (!"PROVIDER".equals(provider.role())) {
            throw new InvalidExchangeException("Provider must have PROVIDER role");
        }

        if (!"RECEIVER".equals(receiver.role())) {
            throw new InvalidExchangeException("Receiver must have RECEIVER role");
        }

        if (request.providerId().equals(request.receiverId())) {
            throw new InvalidExchangeException("Provider and Receiver cannot be the same user");
        }
    }
}