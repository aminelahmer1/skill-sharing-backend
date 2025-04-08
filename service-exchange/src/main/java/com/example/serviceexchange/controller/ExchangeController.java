package com.example.serviceexchange.controller;

import com.example.serviceexchange.dto.ExchangeRequest;
import com.example.serviceexchange.dto.ExchangeResponse;
import com.example.serviceexchange.service.ExchangeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/exchanges")
@RequiredArgsConstructor
public class ExchangeController {

    private final ExchangeService exchangeService;

    @PostMapping
    @PreAuthorize("hasRole('RECEIVER')")
    public ResponseEntity<ExchangeResponse> createExchange(
            @RequestBody @Valid ExchangeRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(exchangeService.createExchange(request, jwt));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("@exchangeValidator.isExchangeParticipant(#id, #jwt)")
    public ResponseEntity<ExchangeResponse> updateStatus(
            @PathVariable Integer id,
            @RequestParam String status,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(exchangeService.updateStatus(id, status, jwt));
    }

    @PutMapping("/{id}/rate")
    @PreAuthorize("@exchangeValidator.isReceiver(#id, #jwt)")
    public ResponseEntity<ExchangeResponse> rateExchange(
            @PathVariable Integer id,
            @RequestParam Integer rating,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(exchangeService.rateExchange(id, rating, jwt));
    }

    @GetMapping("/user/me")
    public ResponseEntity<List<ExchangeResponse>> getUserExchanges(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(exchangeService.getUserExchanges(jwt));
    }
}