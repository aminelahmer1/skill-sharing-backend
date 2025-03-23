package com.example.serviceskill.service;


import com.example.serviceskill.dto.SkillRequest;
import com.example.serviceskill.dto.SkillResponse;
import com.example.serviceskill.entity.Category;
import com.example.serviceskill.entity.Skill;
import org.springframework.stereotype.Service;

@Service
public class SkillMapper {
    public Skill toSkill(SkillRequest request) {
        return Skill.builder()
                .id(request.id())
                .name(request.name())
                .description(request.description())
                .availableQuantity(request.availableQuantity())
                .price(request.price())
                .nbInscrits(0) // Initialisé à 0 par défaut
                .category(
                        Category.builder()
                                .id(request.categoryId())
                                .build()
                )
                .userId(request.userId())
                .build();
    }

    public SkillResponse toSkillResponse(Skill skill) {
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