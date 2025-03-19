package com.example.serviceexchange.service;

import com.example.serviceexchange.FeignClient.SkillServiceClient;
import com.example.serviceexchange.FeignClient.UserServiceClient;
import com.example.serviceexchange.entity.Exchange;
import com.example.serviceexchange.entity.ExchangeStatus;
import com.example.serviceexchange.repository.ExchangeRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ExchangeService {
    private final ExchangeRepository exchangeRepository;

    private final UserServiceClient userServiceClient;

    private final SkillServiceClient skillServiceClient;

    public Exchange createExchange(Long providerId, Long receiverId, Long skillId) {
        // Vérifier que le provider et le receiver existent
        userServiceClient.getUserById(providerId);
        userServiceClient.getUserById(receiverId);

        // Vérifier que la compétence existe
        skillServiceClient.getSkillById(skillId.intValue());

        // Créer l'échange
        Exchange exchange = new Exchange();
        exchange.setProviderId(providerId);
        exchange.setReceiverId(receiverId);
        exchange.setSkillId(skillId);
        exchange.setExchangeDate(LocalDateTime.now());
        exchange.setStatus(ExchangeStatus.PENDING);

        return exchangeRepository.save(exchange);
    }
}
