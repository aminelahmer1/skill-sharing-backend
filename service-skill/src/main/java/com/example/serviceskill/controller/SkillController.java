package com.example.serviceskill.controller;
import com.example.serviceskill.dto.SkillRequest;
import com.example.serviceskill.dto.SkillResponse;
import com.example.serviceskill.service.SkillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/skills")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;

    @PostMapping
    @PreAuthorize("hasRole('PRODUCER')")
    public ResponseEntity<Integer> createSkill(
            @RequestBody @Valid SkillRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(skillService.createSkill(request, jwt));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SkillResponse> getSkill(@PathVariable Integer id) {
        return ResponseEntity.ok(skillService.findById(id));
    }

    @GetMapping
    public ResponseEntity<List<SkillResponse>> getAllSkills() {
        return ResponseEntity.ok(skillService.findAll());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PRODUCER')")
    public ResponseEntity<SkillResponse> updateSkill(
            @PathVariable Integer id,
            @RequestBody @Valid SkillRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(skillService.updateSkill(id, request, jwt));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PRODUCER')")
    public ResponseEntity<Void> deleteSkill(
            @PathVariable Integer id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        skillService.deleteSkill(id, jwt);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/register")
    @PreAuthorize("hasRole('RECEIVER')")
    public ResponseEntity<Void> registerForSkill(
            @PathVariable Integer id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        skillService.registerForSkill(id, jwt);
        return ResponseEntity.noContent().build();
    }
}