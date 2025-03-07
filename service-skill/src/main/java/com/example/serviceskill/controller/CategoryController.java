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
@RequestMapping("/api/v1/categories") // Versioning de l'API
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


}