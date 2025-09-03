package com.example.notification.client;

import com.example.notification.dto.SkillResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "service-skill", url = "${application.config.skill-url}")
public interface SkillServiceClient {

    @GetMapping("/api/v1/skills/{id}")
    SkillResponse getSkillById(@PathVariable("id") Integer id);
}