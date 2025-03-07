package com.example.serviceskill.controller;

import com.example.serviceskill.dto.SkillRequest;
import com.example.serviceskill.dto.SkillResponse;
import com.example.serviceskill.service.SkillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/skills") // Versioning de l'API
@RequiredArgsConstructor
public class SkillController {

    private final SkillService service;

    // Créer une compétence
    @PostMapping("/create")
    public ResponseEntity<Integer> createSkill(
            @RequestBody @Valid SkillRequest request
    ) {
        return ResponseEntity.ok(service.createSkill(request));
    }

    // Récupérer une compétence par ID
    @GetMapping("/{skill-id}")
    public ResponseEntity<SkillResponse> getSkillById(
            @PathVariable("skill-id") Integer skillId
    ) {
        return ResponseEntity.ok(service.findById(skillId));
    }

    // Récupérer toutes les compétences
    @GetMapping("/all")
    public ResponseEntity<List<SkillResponse>> getAllSkills() {
        return ResponseEntity.ok(service.findAll());
    }

    // Mettre à jour une compétence


}