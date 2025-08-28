package com.example.serviceskill.controller;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
        name = "service-exchange",
        url = "${application.config.exchange-url:http://localhost:8822/api/v1/exchanges}"
)
public interface ExchangeServiceClient {

    @DeleteMapping("/skill/{skillId}/cleanup")
    void deleteExchangesBySkillId(
            @PathVariable Integer skillId,
            @RequestHeader("Authorization") String token
    );

    @GetMapping("/skill/{skillId}/exists")
    boolean hasExchangesForSkill(
            @PathVariable Integer skillId,
            @RequestHeader("Authorization") String token
    );
}