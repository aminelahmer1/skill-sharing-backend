package com.example.serviceskill.controller;

import com.example.serviceskill.dto.SkillRequest;
import com.example.serviceskill.dto.SkillResponse;
import com.example.serviceskill.exception.InscriptionLimitExceededException;
import com.example.serviceskill.exception.SkillNotFoundException;
import com.example.serviceskill.service.SkillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
/*import org.springframework.security.access.prepost.PreAuthorize;*/
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/skills")
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
    @PutMapping("/update/{skill-id}")
    public ResponseEntity<SkillResponse> updateSkill(
            @PathVariable("skill-id") Integer skillId,
            @RequestBody @Valid SkillRequest request
    ) {
        return ResponseEntity.ok(service.updateSkill(skillId, request));
    }

    // Supprimer une compétence
    @DeleteMapping("/delete/{skill-id}")
    public ResponseEntity<Void> deleteSkill(
            @PathVariable("skill-id") Integer skillId
    ) {
        service.deleteSkill(skillId);
        return ResponseEntity.noContent().build();
    }

    // Incrémenter le compteur d'inscriptions

    @PostMapping("/{skill-id}/increment")
    public ResponseEntity<Void> incrementNbInscrits(
            @PathVariable("skill-id") Integer skillId
    ) {
        service.incrementNbInscrits(skillId);
        return ResponseEntity.noContent().build();
    }

    // Gestion des exceptions
    @ExceptionHandler(InscriptionLimitExceededException.class)
    public ResponseEntity<String> handleInscriptionLimitExceededException(
            InscriptionLimitExceededException ex
    ) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(SkillNotFoundException.class)
    public ResponseEntity<String> handleSkillNotFoundException(
            SkillNotFoundException ex
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @GetMapping("/search")
    public ResponseEntity<List<SkillResponse>> searchSkills(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice
    ) {
        return ResponseEntity.ok(service.searchSkills(keyword, city, categoryId, minPrice, maxPrice));
    }
}