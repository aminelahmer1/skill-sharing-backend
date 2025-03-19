package com.example.serviceexchange.controller;


import com.example.serviceexchange.entity.Exchange;
import com.example.serviceexchange.service.ExchangeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/exchanges")
@RequiredArgsConstructor
public class ExchangeController {
    private final ExchangeService exchangeService;

    @PostMapping("/create")
    public ResponseEntity<Exchange> createExchange(
            @RequestParam Long providerId,
            @RequestParam Long receiverId,
            @RequestParam Long skillId
    ) {
        Exchange exchange = exchangeService.createExchange(providerId, receiverId, skillId);
        return ResponseEntity.ok(exchange);
    }
}