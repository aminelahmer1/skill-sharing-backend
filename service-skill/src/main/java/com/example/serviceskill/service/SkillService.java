package com.example.serviceskill.service;

import com.example.serviceskill.controller.UserServiceClient;
import com.example.serviceskill.dto.SkillRequest;
import com.example.serviceskill.dto.SkillResponse;
import com.example.serviceskill.dto.UserResponse;
import com.example.serviceskill.entity.Category;
import com.example.serviceskill.entity.Skill;
import com.example.serviceskill.exception.CategoryNotFoundException;
import com.example.serviceskill.exception.InscriptionLimitExceededException;
import com.example.serviceskill.exception.SkillNotFoundException;
import com.example.serviceskill.repository.CategoryRepository;
import com.example.serviceskill.repository.SkillRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SkillService {
private  final CategoryRepository categoryRepository;
    private final SkillRepository repository;
    private final SkillMapper mapper;
    private final UserServiceClient userServiceClient;
    public Integer createSkill(SkillRequest request) {
        // Récupérer les informations de l'utilisateur via Feign
        UserResponse user = userServiceClient.getUserById(request.userId());

        // Vérifier que l'utilisateur est un PROVIDER
        if (!user.role().equals("ROLE_PROVIDER")) {
            throw new RuntimeException("Only users with PROVIDER role can create skills");
        }

        // Vérifier que la catégorie existe
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new CategoryNotFoundException("Category not found"));

        // Créer la compétence
        Skill skill = Skill.builder()
                .name(request.name())
                .description(request.description())
                .availableQuantity(request.availableQuantity())
                .price(request.price())
                .nbInscrits(0)
                .category(category)
                .userId(request.userId())
                .build();

        return repository.save(skill).getId();
    }

    public SkillResponse findById(Integer id) {
        return repository.findById(id)
                .map(mapper::toSkillResponse)
                .orElseThrow(() -> new SkillNotFoundException("Skill not found with ID: " + id));
    }

    public List<SkillResponse> findAll() {
        return repository.findAll()
                .stream()
                .map(mapper::toSkillResponse)
                .collect(Collectors.toList());
    }
    // Mettre à jour une compétence
    public SkillResponse updateSkill(Integer id, SkillRequest request) {
        Skill existingSkill = repository.findById(id)
                .orElseThrow(() -> new SkillNotFoundException("Skill not found with ID: " + id));
        existingSkill.setName(request.name());
        existingSkill.setDescription(request.description());
        existingSkill.setAvailableQuantity(request.availableQuantity());
        existingSkill.setPrice(request.price());

        repository.save(existingSkill);
        return mapper.toSkillResponse(existingSkill);
    }

    // Supprimer une compétence
    public void deleteSkill(Integer id) {
        if (!repository.existsById(id)) {
            throw new SkillNotFoundException("Skill not found with ID: " + id);
        }
        repository.deleteById(id);
    }
    @Transactional
    public void incrementNbInscrits(Integer skillId) {
        Skill skill = repository.findById(skillId)
                .orElseThrow(() -> new SkillNotFoundException("Skill not found with ID: " + skillId));

        // Vérifier si le nombre d'inscriptions dépasse la quantité disponible
        if (skill.getNbInscrits() >= skill.getAvailableQuantity()) {
            throw new InscriptionLimitExceededException(
                    "Inscription limit exceeded for skill with ID: " + skillId +
                            ". Available quantity: " + skill.getAvailableQuantity() +
                            ", Current inscriptions: " + skill.getNbInscrits()
            );
        }

        // Incrémenter le compteur d'inscriptions
        skill.setNbInscrits(skill.getNbInscrits() + 1);
        repository.save(skill);
    }
    public List<SkillResponse> searchSkills(
            String keyword,  // Mot-clé pour le nom ou la description
            String city,     // Ville
            Integer categoryId,  // ID de la catégorie
            Double minPrice,  // Prix minimum
            Double maxPrice   // Prix maximum
    ) {
        return repository.findByKeywordAndFilters(keyword, categoryId, minPrice, maxPrice)
                .stream()
                .map(this::toSkillResponse)
                .collect(Collectors.toList());
    }

    private SkillResponse toSkillResponse(Skill skill) {
        return new SkillResponse(
                skill.getId(),
                skill.getName(),
                skill.getDescription(),
                skill.getAvailableQuantity(),
                skill.getPrice(),
                skill.getNbInscrits(),
                skill.getCategory().getId(),
                skill.getCategory().getName(),
                skill.getCategory().getDescription(),
                skill.getUserId()
        );
    }
}







