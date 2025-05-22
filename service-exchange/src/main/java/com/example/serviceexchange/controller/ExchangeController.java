package com.example.serviceexchange.controller;

import com.example.serviceexchange.dto.ExchangeRequest;
import com.example.serviceexchange.dto.ExchangeResponse;
import com.example.serviceexchange.service.ExchangeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
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

    @PutMapping("/{id}/accept")
    @PreAuthorize("hasRole('PRODUCER')")
    public ResponseEntity<ExchangeResponse> acceptExchange(
            @PathVariable Integer id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(exchangeService.acceptExchange(id, jwt));
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasRole('PRODUCER')")
    public ResponseEntity<ExchangeResponse> rejectExchange(
            @PathVariable Integer id,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(exchangeService.rejectExchange(id, reason, jwt));
    }

    @PostMapping("/{skillId}/start")
    @PreAuthorize("hasRole('PRODUCER')")
    public ResponseEntity<Void> startSession(
            @PathVariable Integer skillId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        exchangeService.startSession(skillId, jwt);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{skillId}/complete")
    @PreAuthorize("hasRole('PRODUCER')")
    public ResponseEntity<Void> completeSession(
            @PathVariable Integer skillId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        exchangeService.completeSession(skillId, jwt);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/my-exchanges")
    public ResponseEntity<List<ExchangeResponse>> getUserExchanges(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(exchangeService.getUserExchanges(jwt));
    }
}