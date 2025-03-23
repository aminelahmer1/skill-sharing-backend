package com.example.serviceexchange.controller;

import com.example.serviceexchange.dto.ExchangeRequest;
import com.example.serviceexchange.dto.ExchangeResponse;
import com.example.serviceexchange.service.ExchangeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/exchanges")
@RequiredArgsConstructor
public class ExchangeController {

    private final ExchangeService exchangeService;

    // Créer un échange
    @PostMapping
    public ResponseEntity<ExchangeResponse> createExchange(@RequestBody @Valid ExchangeRequest request) {
        return ResponseEntity.ok(exchangeService.createExchange(request));
    }

    // Mettre à jour le statut d'un échange
    @PutMapping("/{exchange-id}/status")
    public ResponseEntity<ExchangeResponse> updateExchangeStatus(
            @PathVariable("exchange-id") Integer exchangeId,
            @RequestParam String status
    ) {
        return ResponseEntity.ok(exchangeService.updateExchangeStatus(exchangeId, status));
    }

    // Noter un échange (seulement providerRating)
    @PutMapping("/{exchange-id}/rate")
    public ResponseEntity<ExchangeResponse> rateExchange(
            @PathVariable("exchange-id") Integer exchangeId,
            @RequestParam Integer providerRating
    ) {
        return ResponseEntity.ok(exchangeService.rateExchange(exchangeId, providerRating));
    }

    // Récupérer tous les échanges d'un utilisateur
    @GetMapping("/user/{user-id}")
    public ResponseEntity<List<ExchangeResponse>> getExchangesByUserId(
            @PathVariable("user-id") Long userId
    ) {
        return ResponseEntity.ok(exchangeService.getExchangesByUserId(userId));
    }
}