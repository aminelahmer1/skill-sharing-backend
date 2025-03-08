package com.example.serviceskill.service;

import com.example.serviceskill.dto.SkillRequest;
import com.example.serviceskill.dto.SkillResponse;
import com.example.serviceskill.entity.Skill;
import com.example.serviceskill.exception.InscriptionLimitExceededException;
import com.example.serviceskill.exception.SkillNotFoundException;
import com.example.serviceskill.repository.SkillRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SkillService {

    private final SkillRepository repository;
    private final SkillMapper mapper;

    public Integer createSkill(SkillRequest request) {
        Skill skill = mapper.toSkill(request);
        skill.setNbInscrits(0); // Initialiser le compteur à 0
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
}







