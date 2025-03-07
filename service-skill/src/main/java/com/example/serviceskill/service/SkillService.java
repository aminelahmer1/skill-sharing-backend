package com.example.serviceskill.service;

import com.example.serviceskill.dto.SkillRequest;
import com.example.serviceskill.dto.SkillResponse;
import com.example.serviceskill.entity.Skill;
import com.example.serviceskill.handler.SkillNotFoundException;
import com.example.serviceskill.repository.SkillRepository;
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
        var skill = mapper.toSkill(request);
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

}







