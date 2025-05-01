package com.example.serviceskill.service;

import com.example.serviceskill.controller.UserServiceClient;
import com.example.serviceskill.dto.SkillRequest;
import com.example.serviceskill.dto.SkillResponse;
import com.example.serviceskill.dto.UserResponse;
import com.example.serviceskill.entity.Category;
import com.example.serviceskill.entity.Skill;
import com.example.serviceskill.exception.*;
import com.example.serviceskill.repository.*;
import com.example.serviceskill.service.SkillMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;@Service
@RequiredArgsConstructor
@Slf4j
public class SkillService {

    private final CategoryRepository categoryRepository;
    private final SkillRepository skillRepository;
    private final SkillMapper skillMapper;
    private final UserServiceClient userServiceClient;
    @Transactional
    public Integer createSkill(SkillRequest request, Jwt jwt) {
        // 1. Récupérer l'utilisateur via son keycloakId
        String keycloakId = jwt.getSubject();
        UserResponse user = userServiceClient.getUserByKeycloakId(
                keycloakId,
                "Bearer " + jwt.getTokenValue()
        );

        if (user == null) {
            throw new UserNotFoundException("User not found for Keycloak ID: " + keycloakId);
        }

        // 2. Vérifier le rôle
        if (!user.roles().contains("PRODUCER")) {
            throw new AccessDeniedException("Only producers can create skills");
        }

        // 3. Créer la compétence avec l'ID utilisateur
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new CategoryNotFoundException("Category not found"));

        Skill skill = skillMapper.toSkill(request, user.id()); // Utiliser l'ID DB ici
        skill.setCategory(category);
        skill.setUserId(user.id()); // Assurer que userId est bien défini

        log.info("Skill created by user ID: {} (Keycloak ID: {})", user.id(), keycloakId);
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

        Long currentUserId = Long.parseLong(jwt.getSubject());
        if (!skill.getUserId().equals(currentUserId)) {
            throw new AccessDeniedException("You can only update your own skills");
        }

        skill.setName(request.name());
        skill.setDescription(request.description());
        skill.setAvailableQuantity(request.availableQuantity());
        skill.setPrice(request.price());

        log.info("Skill with ID {} updated by producer ID: {}", id, currentUserId);
        return skillMapper.toSkillResponse(skillRepository.save(skill));
    }
    @Transactional
    public void deleteSkill(Integer id, Jwt jwt) {
        Skill skill = skillRepository.findById(id)
                .orElseThrow(() -> new SkillNotFoundException("Skill not found"));

        Long currentUserId = Long.parseLong(jwt.getSubject());
        if (!skill.getUserId().equals(currentUserId)) {
            throw new AccessDeniedException("You can only delete your own skills");
        }

        skillRepository.delete(skill);
        log.info("Skill with ID {} deleted by producer ID: {}", id, currentUserId);
    }

    @Transactional
    public void registerForSkill(Integer skillId, Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null || !roles.contains("RECEIVER")) {
            throw new AccessDeniedException("Only receivers can register for skills");
        }

        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new SkillNotFoundException("Skill not found"));

        if (skill.getNbInscrits() >= skill.getAvailableQuantity()) {
            throw new InscriptionLimitExceededException("No available slots");
        }

        skill.setNbInscrits(skill.getNbInscrits() + 1);
        skillRepository.save(skill);
        log.info("User ID {} registered for skill ID {}", jwt.getSubject(), skillId);
    }
}
