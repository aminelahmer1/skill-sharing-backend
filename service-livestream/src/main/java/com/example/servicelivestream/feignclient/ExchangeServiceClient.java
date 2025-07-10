package com.example.servicelivestream.feignclient;

import com.example.servicelivestream.dto.ExchangeResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "service-exchange", url = "${application.config.exchange-url}")
public interface ExchangeServiceClient {
    @GetMapping("/{id}")
    ExchangeResponse getExchangeById(@PathVariable Integer id, @RequestHeader("Authorization") String token);


        @GetMapping("/skill/{skillId}")
        List<ExchangeResponse> getExchangesBySkillId(
                @PathVariable("skillId") Integer skillId,
                @RequestHeader("Authorization") String token
        );

    @PutMapping("/{id}/status")
    void updateExchangeStatus(
            @PathVariable("id") Integer id,
            @RequestParam("status") String status,
            @RequestHeader("Authorization") String token
    );
}