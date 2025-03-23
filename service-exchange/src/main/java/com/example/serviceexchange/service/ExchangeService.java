package com.example.serviceexchange.service;
import com.example.serviceexchange.FeignClient.SkillServiceClient;
import com.example.serviceexchange.FeignClient.UserServiceClient;

import com.example.serviceexchange.dto.ExchangeRequest;
import com.example.serviceexchange.dto.ExchangeResponse;
import com.example.serviceexchange.dto.UserResponse;
import com.example.serviceexchange.entity.Exchange;
import com.example.serviceexchange.exception.ExchangeNotFoundException;
import com.example.serviceexchange.repository.ExchangeRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExchangeService {

    private final ExchangeRepository exchangeRepository;
    private final UserServiceClient userServiceClient;
    private final SkillServiceClient skillServiceClient;

    // Créer un échange
    public ExchangeResponse createExchange(ExchangeRequest request) {
        // Récupérer le Provider
        UserResponse provider = userServiceClient.getUserById(request.providerId());
        if (!provider.role().equals("ROLE_PROVIDER")) {
            throw new RuntimeException("Provider ID must belong to a user with ROLE_PROVIDER");
        }

        // Récupérer le Receiver
        UserResponse receiver = userServiceClient.getUserById(request.receiverId());
        if (!receiver.role().equals("ROLE_RECEIVER")) {
            throw new RuntimeException("Receiver ID must belong to a user with ROLE_RECEIVER");
        }

        // Vérifier que le Provider et le Receiver ne sont pas la même personne
        if (request.providerId().equals(request.receiverId())) {
            throw new RuntimeException("Provider and Receiver cannot be the same user");
        }

        // Vérifier que la compétence existe
        skillServiceClient.getSkillById(request.skillId());

        // Créer l'échange
        Exchange exchange = Exchange.builder()
                .providerId(request.providerId())
                .receiverId(request.receiverId())
                .skillId(request.skillId())
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();

        exchange = exchangeRepository.save(exchange);
        return toExchangeResponse(exchange);
    }



    // Mettre à jour le statut d'un échange
    public ExchangeResponse updateExchangeStatus(Integer exchangeId, String status) {
        Exchange exchange = exchangeRepository.findById(exchangeId)
                .orElseThrow(() -> new ExchangeNotFoundException("Exchange not found with ID: " + exchangeId));

        exchange.setStatus(status);
        exchange = exchangeRepository.save(exchange);
        return toExchangeResponse(exchange);
    }

    // Noter un échange (seulement providerRating)
    public ExchangeResponse rateExchange(Integer exchangeId, Integer providerRating) {
        Exchange exchange = exchangeRepository.findById(exchangeId)
                .orElseThrow(() -> new ExchangeNotFoundException("Exchange not found with ID: " + exchangeId));

        exchange.setProviderRating(providerRating);
        exchange = exchangeRepository.save(exchange);
        return toExchangeResponse(exchange);
    }

    // Récupérer tous les échanges d'un utilisateur
    public List<ExchangeResponse> getExchangesByUserId(Long userId) {
        List<Exchange> exchanges = exchangeRepository.findByProviderId(userId);
        exchanges.addAll(exchangeRepository.findByReceiverId(userId));
        return exchanges.stream()
                .map(this::toExchangeResponse)
                .collect(Collectors.toList());
    }

    // Mapper Exchange vers ExchangeResponse
    private ExchangeResponse toExchangeResponse(Exchange exchange) {
        return new ExchangeResponse(
                exchange.getId(),
                exchange.getProviderId(),
                exchange.getReceiverId(),
                exchange.getSkillId(),
                exchange.getStatus(),
                exchange.getCreatedAt(),
                exchange.getProviderRating()
        );
    }
}