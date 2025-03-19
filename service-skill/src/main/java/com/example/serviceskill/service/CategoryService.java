package com.example.serviceskill.service;

//import com.example.serviceskill.controller.UserServiceClient;
import com.example.serviceskill.dto.CategoryRequest;
import com.example.serviceskill.dto.CategoryResponse;
import com.example.serviceskill.entity.Category;
import com.example.serviceskill.exception.CategoryNotFoundException;
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
    public CategoryResponse findById(Integer id) {
        return repository.findById(id)
                .map(mapper::toCategoryResponse)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found with ID: " + id));
    }

    // Mettre à jour une catégorie
    public CategoryResponse updateCategory(Integer id, CategoryRequest request) {
        Category existingCategory = repository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found with ID: " + id));
        existingCategory.setName(request.name());
        existingCategory.setDescription(request.description());
        repository.save(existingCategory);
        return mapper.toCategoryResponse(existingCategory);
    }

    // Supprimer une catégorie
    public void deleteCategory(Integer id) {
        if (!repository.existsById(id)) {
            throw new CategoryNotFoundException("Category not found with ID: " + id);
        }
        repository.deleteById(id);
    }

}