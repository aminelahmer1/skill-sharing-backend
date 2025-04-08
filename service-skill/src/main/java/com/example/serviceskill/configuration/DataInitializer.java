package com.example.serviceskill.configuration;

import com.example.serviceskill.entity.Category;
import com.example.serviceskill.repository.CategoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner initCategories(CategoryRepository categoryRepository) {
        return args -> {

            if (categoryRepository.count() == 0) {

                categoryRepository.save(Category.builder()
                        .id(1)
                        .name("Développement Web")
                        .description("Compétences en création de sites web")
                        .build());

                categoryRepository.save(Category.builder()
                        .id(2)
                        .name("Design Graphique")
                        .description("Création d'éléments visuels")
                        .build());


            }
        };
    }
}