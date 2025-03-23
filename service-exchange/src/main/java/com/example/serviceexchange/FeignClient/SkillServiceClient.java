package com.example.serviceexchange.FeignClient;


import com.example.serviceexchange.dto.SkillResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "service-skill", url = "${application.config.skill-url}")
public interface SkillServiceClient {

    @GetMapping("/{skill-id}")
    SkillResponse getSkillById(@PathVariable("skill-id") Integer skillId);
}