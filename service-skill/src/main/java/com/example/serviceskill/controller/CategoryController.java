package com.example.serviceskill.controller;

import com.example.serviceskill.dto.CategoryRequest;
import com.example.serviceskill.dto.CategoryResponse;
import com.example.serviceskill.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService service;

    // Créer une catégorie
    @PostMapping("/create")
    public ResponseEntity<Integer> createCategory(
            @RequestBody @Valid CategoryRequest request
    ) {
        return ResponseEntity.ok(service.createCategory(request));
    }

    // Récupérer toutes les catégories
    @GetMapping("/all")
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {
        return ResponseEntity.ok(service.findAll());
    }

    // Récupérer une catégorie par ID
    @GetMapping("/{category-id}")
    public ResponseEntity<CategoryResponse> getCategoryById(
            @PathVariable("category-id") Integer categoryId
    ) {
        return ResponseEntity.ok(service.findById(categoryId));
    }

    // Mettre à jour une catégorie
    @PutMapping("/update/{category-id}")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable("category-id") Integer categoryId,
            @RequestBody @Valid CategoryRequest request
    ) {
        return ResponseEntity.ok(service.updateCategory(categoryId, request));
    }

    // Supprimer une catégorie
    @DeleteMapping("/delete/{category-id}")
    public ResponseEntity<Void> deleteCategory(
            @PathVariable("category-id") Integer categoryId
    ) {
        service.deleteCategory(categoryId);
        return ResponseEntity.noContent().build();
    }
}