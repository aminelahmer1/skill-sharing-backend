package com.example.serviceexchange.FeignClient;


import com.example.serviceexchange.dto.SkillResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "service-skill", url = "${application.config.skill-url}")
public interface SkillServiceClient {

    @GetMapping("/{skill-id}")
    SkillResponse getSkillById(@PathVariable("skill-id") Integer skillId);


    @PostMapping("/{skillId}/increment-inscrits")
    void incrementInscrits(@PathVariable Integer skillId, @RequestHeader("Authorization") String token);

    @PostMapping("/{skillId}/decrement-inscrits")
    void decrementInscrits(@PathVariable Integer skillId, @RequestHeader("Authorization") String token);
}