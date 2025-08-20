package com.example.servicemessagerie.feignclient;

import com.example.servicemessagerie.dto.SkillResponse;
import com.example.servicemessagerie.dto.ExchangeResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@FeignClient(name = "service-skill", url = "${application.config.skill-url}")
public interface SkillServiceClient {
    @GetMapping("/{skillId}")
    SkillResponse getSkillById(@PathVariable Integer skillId);

    @GetMapping("/by-user/{userId}")
    List<SkillResponse> getSkillsByUserId(@PathVariable Long userId, @RequestHeader("Authorization") String token);
}