package com.example.serviceskill.configuration;

import com.example.serviceskill.entity.Category;
import com.example.serviceskill.repository.CategoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Configuration
@Slf4j
public class DataInitializer {

    @Bean
    @Transactional
    public CommandLineRunner initCategories(CategoryRepository categoryRepository) {
        return args -> {
            log.info("===== Starting Category Initialization =====");

            // Définir toutes les catégories à avoir dans la base
            Map<Integer, Category> targetCategories = getTargetCategories();

            // Récupérer les catégories existantes
            List<Category> existingCategories = categoryRepository.findAll();
            Map<Integer, Category> existingMap = new HashMap<>();
            for (Category cat : existingCategories) {
                existingMap.put(cat.getId(), cat);
            }

            // Statistiques
            int added = 0, updated = 0, deleted = 0;

            // 1. Supprimer les catégories qui ne sont plus dans la liste cible
            for (Integer existingId : existingMap.keySet()) {
                if (!targetCategories.containsKey(existingId)) {
                    categoryRepository.deleteById(existingId);
                    log.info("❌ Deleted category ID: {}", existingId);
                    deleted++;
                }
            }

            // 2. Ajouter ou mettre à jour les catégories
            for (Map.Entry<Integer, Category> entry : targetCategories.entrySet()) {
                Integer id = entry.getKey();
                Category targetCategory = entry.getValue();

                if (existingMap.containsKey(id)) {
                    // Vérifier si une mise à jour est nécessaire
                    Category existing = existingMap.get(id);
                    if (!existing.getName().equals(targetCategory.getName()) ||
                            !existing.getDescription().equals(targetCategory.getDescription())) {

                        existing.setName(targetCategory.getName());
                        existing.setDescription(targetCategory.getDescription());
                        categoryRepository.save(existing);
                        log.info("🔄 Updated category: {} - {}", id, targetCategory.getName());
                        updated++;
                    }
                } else {
                    // Ajouter nouvelle catégorie
                    categoryRepository.save(targetCategory);
                    log.info("✅ Added new category: {} - {}", id, targetCategory.getName());
                    added++;
                }
            }

            // 3. Afficher le résumé
            long finalCount = categoryRepository.count();
            log.info("===== Category Initialization Complete =====");
            log.info("📊 Summary: Added: {}, Updated: {}, Deleted: {}", added, updated, deleted);
            log.info("📦 Total categories in database: {}", finalCount);

            // Vérification finale
            if (finalCount != targetCategories.size()) {
                log.warn("⚠️ Warning: Expected {} categories but found {}",
                        targetCategories.size(), finalCount);
            }
        };
    }

    private Map<Integer, Category> getTargetCategories() {
        Map<Integer, Category> categories = new LinkedHashMap<>();

        // TECHNOLOGIE
        categories.put(1, Category.builder()
                .id(1)
                .name("Développement Web")
                .description("HTML, CSS, JavaScript, React, Angular, Vue.js, développement fullstack")
                .build());

        categories.put(2, Category.builder()
                .id(2)
                .name("Design Graphique")
                .description("Photoshop, Illustrator, UI/UX, logos, création visuelle")
                .build());

        categories.put(3, Category.builder()
                .id(3)
                .name("Programmation")
                .description("Python, Java, C++, développement mobile, algorithmes")
                .build());

        // LANGUES
        categories.put(4, Category.builder()
                .id(4)
                .name("Langues Étrangères")
                .description("Anglais, Français, Espagnol, Allemand, Arabe, conversation et grammaire")
                .build());

        // ARTS & CRÉATIVITÉ
        categories.put(5, Category.builder()
                .id(5)
                .name("Musique")
                .description("Instruments, chant, composition, théorie musicale, MAO")
                .build());

        categories.put(6, Category.builder()
                .id(6)
                .name("Photographie & Vidéo")
                .description("Techniques photo, montage vidéo, retouche, production audiovisuelle")
                .build());

        categories.put(7, Category.builder()
                .id(7)
                .name("Arts & Artisanat")
                .description("Peinture, dessin, sculpture, couture, bijouterie, DIY")
                .build());

        // CUISINE
        categories.put(8, Category.builder()
                .id(8)
                .name("Cuisine & Pâtisserie")
                .description("Recettes du monde, pâtisserie, boulangerie, cuisine santé")
                .build());

        // BUSINESS
        categories.put(9, Category.builder()
                .id(9)
                .name("Marketing Digital")
                .description("SEO, réseaux sociaux, publicité en ligne, content marketing")
                .build());

        categories.put(10, Category.builder()
                .id(10)
                .name("Business & Entrepreneuriat")
                .description("Création d'entreprise, stratégie, management, leadership")
                .build());

        categories.put(11, Category.builder()
                .id(11)
                .name("Finance")
                .description("Comptabilité, investissement, gestion financière personnelle")
                .build());

        // DÉVELOPPEMENT PERSONNEL
        categories.put(12, Category.builder()
                .id(12)
                .name("Développement Personnel")
                .description("Coaching, productivité, confiance en soi, organisation")
                .build());

        categories.put(13, Category.builder()
                .id(13)
                .name("Bien-être & Santé")
                .description("Yoga, méditation, nutrition, gestion du stress")
                .build());

        // SPORT
        categories.put(14, Category.builder()
                .id(14)
                .name("Sport & Fitness")
                .description("Musculation, cardio, sports collectifs, arts martiaux")
                .build());

        categories.put(15, Category.builder()
                .id(15)
                .name("Danse")
                .description("Salsa, hip-hop, contemporaine, classique, danse orientale")
                .build());

        // ÉDUCATION
        categories.put(16, Category.builder()
                .id(16)
                .name("Soutien Scolaire")
                .description("Mathématiques, sciences, langues, aide aux devoirs")
                .build());

        // LIFESTYLE
        categories.put(17, Category.builder()
                .id(17)
                .name("Mode & Beauté")
                .description("Conseils mode, maquillage, coiffure, soins personnels")
                .build());

        categories.put(18, Category.builder()
                .id(18)
                .name("Décoration & Jardinage")
                .description("Design d'intérieur, DIY déco, jardinage, plantes")
                .build());

        // COMMUNICATION
        categories.put(19, Category.builder()
                .id(19)
                .name("Communication")
                .description("Prise de parole, présentation, rédaction, storytelling")
                .build());

        // JEUX & LOISIRS
        categories.put(20, Category.builder()
                .id(20)
                .name("Jeux & Loisirs")
                .description("Gaming, échecs, jeux de société, culture générale")
                .build());

        return categories;
    }
}