package com.example.serviceskill.controller;
import com.example.serviceskill.dto.SkillRequest;
import com.example.serviceskill.dto.SkillResponse;
import com.example.serviceskill.dto.UserResponse;
import com.example.serviceskill.service.SkillService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/skills")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;
    private final ObjectMapper objectMapper = new ObjectMapper();
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

    @GetMapping("/producer/{producerId}")
    public ResponseEntity<List<SkillResponse>> getSkillsByProducer(
            @PathVariable Long producerId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(skillService.findSkillsByProducerId(producerId, jwt));
    }

    @GetMapping("/my-skills")
    @PreAuthorize("hasRole('PRODUCER')")
    public ResponseEntity<List<SkillResponse>> getMySkills(
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(skillService.findMySkills(jwt));
    }

    @PostMapping(value = "/with-picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('PRODUCER')")
    public ResponseEntity<Integer> createSkillWithPicture(
            @RequestPart("skill") String skillJson,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) throws JsonProcessingException {

        SkillRequest request = objectMapper.readValue(skillJson, SkillRequest.class);
        return ResponseEntity.ok(skillService.createSkillWithPicture(request, file, jwt));
    }

    @PatchMapping(value = "/{id}/picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('PRODUCER')")
    public ResponseEntity<SkillResponse> updateSkillPicture(
            @PathVariable Integer id,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(skillService.updateSkillPicture(id, file, jwt));
    }

    @PostMapping("/{skillId}/increment-inscrits")
    @PreAuthorize("hasRole('RECEIVER')")
    public ResponseEntity<Void> incrementInscrits(@PathVariable Integer skillId, @AuthenticationPrincipal Jwt jwt) {
        skillService.incrementInscrits(skillId, jwt);
        return ResponseEntity.ok().build();
    }
    @PostMapping("/{skillId}/decrement-inscrits")
    @PreAuthorize("hasRole('PRODUCER')")
    public ResponseEntity<Void> decrementInscrits(@PathVariable Integer skillId, @AuthenticationPrincipal Jwt jwt) {
        skillService.decrementInscrits(skillId, jwt);
        return ResponseEntity.ok().build();
    }

}