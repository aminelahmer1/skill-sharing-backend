package com.example.serviceskill.service;

import com.example.serviceskill.dto.CategoryRequest;
import com.example.serviceskill.dto.CategoryResponse;
import com.example.serviceskill.entity.Category;
import org.springframework.stereotype.Service;

@Service
public class CategoryMapper {
    public Category toCategory(CategoryRequest request) {
        return Category.builder()
                .id(request.id())
                .name(request.name())
                .description(request.description())
                .build();
    }

    public CategoryResponse toCategoryResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription()
        );
    }
}

