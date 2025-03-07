package com.example.serviceskill.service;

import com.example.serviceskill.dto.CategoryRequest;
import com.example.serviceskill.dto.CategoryResponse;
import com.example.serviceskill.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository repository;
    private final CategoryMapper mapper;

    public Integer createCategory(CategoryRequest request) {
        var category = mapper.toCategory(request);
        return repository.save(category).getId();
    }

    public List<CategoryResponse> findAll() {
        return repository.findAll()
                .stream()
                .map(mapper::toCategoryResponse)
                .collect(Collectors.toList());
    }
}