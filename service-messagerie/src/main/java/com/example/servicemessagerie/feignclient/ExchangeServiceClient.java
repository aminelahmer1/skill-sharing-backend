package com.example.servicemessagerie.feignclient;

import com.example.servicemessagerie.dto.ExchangeResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@FeignClient(name = "service-exchange", url = "${application.config.exchange-url}")
public interface ExchangeServiceClient {
    @GetMapping("/skill/{skillId}")
    List<ExchangeResponse> getExchangesBySkillId(@PathVariable Integer skillId, @RequestHeader("Authorization") String token);
}