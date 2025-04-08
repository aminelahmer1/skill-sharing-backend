package com.example.serviceskill.service;

import com.example.serviceskill.dto.SkillRequest;
import com.example.serviceskill.dto.SkillResponse;
import com.example.serviceskill.entity.Category;
import com.example.serviceskill.entity.Skill;
import com.example.serviceskill.exception.*;
import com.example.serviceskill.repository.*;
import com.example.serviceskill.service.SkillMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SkillService {
    private final CategoryRepository categoryRepository;
    private final SkillRepository skillRepository;
    private final SkillMapper skillMapper;

    @Transactional
    public Integer createSkill(SkillRequest request, Jwt jwt) {
        // Vérification du rôle
        if (!jwt.getClaimAsStringList("roles").contains("PROVIDER")) {
            throw new AccessDeniedException("Only providers can create skills");
        }

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new CategoryNotFoundException("Category not found"));

        Skill skill = skillMapper.toSkill(request, Long.parseLong(jwt.getSubject()));
        skill.setCategory(category);

        return skillRepository.save(skill).getId();
    }

    public SkillResponse findById(Integer id) {
        return skillRepository.findById(id)
                .map(skillMapper::toSkillResponse)
                .orElseThrow(() -> new SkillNotFoundException("Skill not found"));
    }

    public List<SkillResponse> findAll() {
        return skillRepository.findAll().stream()
                .map(skillMapper::toSkillResponse)
                .toList();
    }

    @Transactional
    public SkillResponse updateSkill(Integer id, SkillRequest request, Jwt jwt) {
        Skill skill = skillRepository.findById(id)
                .orElseThrow(() -> new SkillNotFoundException("Skill not found"));

        // Vérification de l'ownership
        if (!skill.getUserId().equals(Long.parseLong(jwt.getSubject()))) {
            throw new AccessDeniedException("You can only update your own skills");
        }

        skill.setName(request.name());
        skill.setDescription(request.description());
        skill.setAvailableQuantity(request.availableQuantity());
        skill.setPrice(request.price());

        return skillMapper.toSkillResponse(skillRepository.save(skill));
    }

    @Transactional
    public void deleteSkill(Integer id, Jwt jwt) {
        Skill skill = skillRepository.findById(id)
                .orElseThrow(() -> new SkillNotFoundException("Skill not found"));

        if (!skill.getUserId().equals(Long.parseLong(jwt.getSubject()))) {
            throw new AccessDeniedException("You can only delete your own skills");
        }

        skillRepository.delete(skill);
    }

    @Transactional
    public void registerForSkill(Integer skillId, Jwt jwt) {
        // Vérification du rôle
        if (!jwt.getClaimAsStringList("roles").contains("RECEIVER")) {
            throw new AccessDeniedException("Only receivers can register for skills");
        }

        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new SkillNotFoundException("Skill not found"));

        if (skill.getNbInscrits() >= skill.getAvailableQuantity()) {
            throw new InscriptionLimitExceededException("No available slots");
        }

        skill.setNbInscrits(skill.getNbInscrits() + 1);
        skillRepository.save(skill);
    }
}