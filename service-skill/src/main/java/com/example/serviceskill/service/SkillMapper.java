package com.example.serviceskill.service;

import com.example.serviceskill.dto.SkillRequest;
import com.example.serviceskill.dto.SkillResponse;
import com.example.serviceskill.entity.Category;
import com.example.serviceskill.entity.Skill;
import org.springframework.stereotype.Service;
@Service
public class SkillMapper {

    public Skill toSkill(SkillRequest request, long userId) {
        return Skill.builder()
                .id(request.id())
                .name(request.name())
                .description(request.description())
                .availableQuantity(request.availableQuantity())
                .price(request.price())
                .nbInscrits(0)
                .category(Category.builder().id(request.categoryId()).build())
                .userId(userId)
                .pictureUrl(request.pictureUrl())
                .streamingDate(request.streamingDate())
                .streamingTime(request.streamingTime())
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
                skill.getUserId(),
                skill.getPictureUrl(),
                skill.getStreamingDate(),
                skill.getStreamingTime()
        );
    }
}