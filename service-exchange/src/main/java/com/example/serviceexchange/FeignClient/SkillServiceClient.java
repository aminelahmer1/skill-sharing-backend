package com.example.serviceexchange.FeignClient;

import com.example.serviceskill.dto.SkillResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "service-skill", url = "http://localhost:8050")
public interface SkillServiceClient {
    @GetMapping("/skills/{skill-id}")
    SkillResponse getSkillById(@PathVariable("skill-id") Integer skillId);
}