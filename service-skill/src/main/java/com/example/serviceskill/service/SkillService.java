package com.example.serviceskill.service;

import com.example.serviceskill.controller.UserServiceClient;
import com.example.serviceskill.dto.SkillRequest;
import com.example.serviceskill.dto.SkillResponse;
import com.example.serviceskill.dto.UserResponse;
import com.example.serviceskill.entity.Category;
import com.example.serviceskill.entity.Skill;
import com.example.serviceskill.exception.CategoryNotFoundException;
import com.example.serviceskill.exception.InscriptionLimitExceededException;
import com.example.serviceskill.exception.SkillNotFoundException;
import com.example.serviceskill.exception.UserNotFoundException;
import com.example.serviceskill.repository.CategoryRepository;
import com.example.serviceskill.repository.SkillRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SkillService {

    private final CategoryRepository categoryRepository;
    private final SkillRepository skillRepository;
    private final SkillMapper skillMapper;
    private final UserServiceClient userServiceClient;
 private  final  FileStorageService fileStorageService;

    private UserResponse getAuthenticatedUser(Jwt jwt) {
        String keycloakId = jwt.getSubject();
        UserResponse user = userServiceClient.getUserByKeycloakId(
                keycloakId,
                "Bearer " + jwt.getTokenValue()
        );

        if (user == null) {
            throw new UserNotFoundException("User not found for Keycloak ID: " + keycloakId);
        }

        return user;
    }

    @Transactional
    public Integer createSkill(SkillRequest request, Jwt jwt) {
        // 1. Récupérer l'utilisateur via son keycloakId
        String keycloakId = jwt.getSubject();
        UserResponse user = userServiceClient.getUserByKeycloakId(
                keycloakId,
                "Bearer " + jwt.getTokenValue()
        );

        if (user == null) {
            throw new UserNotFoundException("User not found for Keycloak ID: " + keycloakId);
        }

        // 2. Vérifier le rôle
        if (!user.roles().contains("PRODUCER")) {
            throw new AccessDeniedException("Only producers can create skills");
        }

        // 3. Créer la compétence avec l'ID utilisateur
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new CategoryNotFoundException("Category not found"));

        validateStreamingDate(request.streamingDate());

        Skill skill = skillMapper.toSkill(request, user.id()); // Utiliser l'ID DB ici
        skill.setCategory(category);
        skill.setUserId(user.id()); // Assurer que userId est bien défini

        log.info("Skill created by user ID: {} (Keycloak ID: {})", user.id(), keycloakId);
        return skillRepository.save(skill).getId();
    }
    public SkillResponse findById(Integer id) {
        return skillRepository.findById(id)
                .map(skillMapper::toSkillResponse)
                .orElseThrow(() -> new SkillNotFoundException("Skill not found"));
    }

    public List<SkillResponse> findAll() {
        return skillRepository.findAll().stream()
                .map(skillMapper::toSkillResponse)
                .toList();
    }

    @Transactional
    public SkillResponse updateSkill(Integer id, SkillRequest request, Jwt jwt) {
        // 1. Récupérer l'utilisateur via son keycloakId
        String keycloakId = jwt.getSubject();
        UserResponse user = userServiceClient.getUserByKeycloakId(
                keycloakId,
                "Bearer " + jwt.getTokenValue()
        );

        if (user == null) {
            throw new UserNotFoundException("User not found for Keycloak ID: " + keycloakId);
        }

        // 2. Vérifier le rôle
        if (!user.roles().contains("PRODUCER")) {
            throw new AccessDeniedException("Only producers can update skills");
        }

        // 3. Vérifier que la compétence existe et appartient à l'utilisateur
        Skill skill = skillRepository.findById(id)
                .orElseThrow(() -> new SkillNotFoundException("Skill not found with ID: " + id));

        if (!skill.getUserId().equals(user.id())) {
            throw new AccessDeniedException("You can only update your own skills");
        }

        // 4. Vérifier que la catégorie existe
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new CategoryNotFoundException("Category not found"));

        validateStreamingDate(request.streamingDate());
        // 5. Mettre à jour la compétence
        skill.setName(request.name());
        skill.setDescription(request.description());
        skill.setAvailableQuantity(request.availableQuantity());
        skill.setPrice(request.price());
        skill.setCategory(category);
        skill.setStreamingDate(request.streamingDate());
        skill.setStreamingTime(request.streamingTime());
        log.info("Skill updated by user ID: {} (Keycloak ID: {})", user.id(), keycloakId);
        return skillMapper.toSkillResponse(skillRepository.save(skill));
    }

    @Transactional
    public void deleteSkill(Integer id, Jwt jwt) {
        // 1. Récupérer l'utilisateur via son keycloakId
        String keycloakId = jwt.getSubject();
        UserResponse user = userServiceClient.getUserByKeycloakId(
                keycloakId,
                "Bearer " + jwt.getTokenValue()
        );

        if (user == null) {
            throw new UserNotFoundException("User not found for Keycloak ID: " + keycloakId);
        }

        // 2. Vérifier le rôle
        if (!user.roles().contains("PRODUCER")) {
            throw new AccessDeniedException("Only producers can delete skills");
        }

        // 3. Vérifier que la compétence existe et appartient à l'utilisateur
        Skill skill = skillRepository.findById(id)
                .orElseThrow(() -> new SkillNotFoundException("Skill not found with ID: " + id));

        if (!skill.getUserId().equals(user.id())) {
            throw new AccessDeniedException("You can only delete your own skills");
        }

        // 4. Supprimer la compétence
        skillRepository.delete(skill);
        log.info("Skill deleted by user ID: {} (Keycloak ID: {})", user.id(), keycloakId);
    }

    @Transactional
    public void registerForSkill(Integer skillId, Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null || !roles.contains("RECEIVER")) {
            throw new AccessDeniedException("Only receivers can register for skills");
        }

        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new SkillNotFoundException("Skill not found"));

        if (skill.getNbInscrits() >= skill.getAvailableQuantity()) {
            throw new InscriptionLimitExceededException("No available slots");
        }

        skill.setNbInscrits(skill.getNbInscrits() + 1);
        skillRepository.save(skill);
        log.info("User ID {} registered for skill ID {}", jwt.getSubject(), skillId);
    }

    public List<SkillResponse> findSkillsByProducerId(Long producerId, Jwt jwt) {
        // 1. Récupérer l'utilisateur demandeur
        UserResponse requestingUser = getAuthenticatedUser(jwt);

        // 2. Récupérer l'utilisateur producteur
        UserResponse producer = userServiceClient.getUserById(
                producerId,
                "Bearer " + jwt.getTokenValue()
        ).getBody();

        if (producer == null) {
            throw new UserNotFoundException("Producer not found with ID: " + producerId);
        }

        // 3. Vérifier que l'utilisateur est bien un PRODUCER
        if (!producer.roles().contains("PRODUCER")) {
            throw new AccessDeniedException("The requested user is not a PRODUCER");
        }

        // 4. Récupérer les compétences
        List<Skill> skills = skillRepository.findByUserId(producerId);

        return skills.stream()
                .map(skillMapper::toSkillResponse)
                .collect(Collectors.toList());
    }

    public List<SkillResponse> findMySkills(Jwt jwt) {
        // 1. Récupérer l'utilisateur connecté
        UserResponse user = getAuthenticatedUser(jwt);

        // 2. Vérifier que c'est bien un PRODUCER
        if (!user.roles().contains("PRODUCER")) {
            throw new AccessDeniedException("Only producers can view their skills");
        }

        // 3. Récupérer les compétences de l'utilisateur
        List<Skill> skills = skillRepository.findByUserId(user.id());

        // 4. Convertir en DTO
        return skills.stream()
                .map(skillMapper::toSkillResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public Integer createSkillWithPicture(SkillRequest request, MultipartFile file, Jwt jwt) {
        String pictureUrl = null;
        if (file != null && !file.isEmpty()) {
            pictureUrl = fileStorageService.storeFile(file);
        }

        SkillRequest requestWithPicture = new SkillRequest(
                request.id(),
                request.name(),
                request.description(),
                request.availableQuantity(),
                request.price(),
                request.categoryId(),
                pictureUrl,
                request.streamingDate(),
                request.streamingTime()
        );

        return createSkill(requestWithPicture, jwt);
    }

    @Transactional
    public SkillResponse updateSkillPicture(Integer id, MultipartFile file, Jwt jwt) {
        // Validation de l'utilisateur
        UserResponse user = getAuthenticatedUser(jwt);

        // Récupération de la compétence
        Skill skill = skillRepository.findById(id)
                .orElseThrow(() -> new SkillNotFoundException("Skill not found"));

        // Vérification des droits
        if (!skill.getUserId().equals(user.id())) {
            throw new AccessDeniedException("You can only update your own skills");
        }

        // Stockage du fichier
        String pictureUrl = fileStorageService.storeFile(file);
        skill.setPictureUrl(pictureUrl);

        // Sauvegarde
        return skillMapper.toSkillResponse(skillRepository.save(skill));
    }
    private void validateStreamingDate(String streamingDate) {
        LocalDate now = LocalDate.now();
        LocalDate maxDate = LocalDate.of(2050, 12, 31);
        LocalDate date = LocalDate.parse(streamingDate, DateTimeFormatter.ISO_LOCAL_DATE);

        if (date.isBefore(now) || date.isAfter(maxDate)) {
            throw new IllegalArgumentException("La date de streaming doit être entre aujourd'hui et le 31 décembre 2050");
        }
    }
    @Transactional
    public void incrementInscrits(Integer skillId, Jwt jwt) {
        UserResponse user = userServiceClient.getUserByKeycloakId(jwt.getSubject(), "Bearer " + jwt.getTokenValue());
        if (!user.roles().contains("RECEIVER")) {
            throw new AccessDeniedException("Only receivers can increment registrations");
        }
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new SkillNotFoundException("Skill not found with ID: " + skillId));
        if (skill.getNbInscrits() >= skill.getAvailableQuantity()) {
            throw new InscriptionLimitExceededException("No available slots");
        }
        skill.incrementRegistrations();
        skillRepository.save(skill);
        log.info("Incremented nbInscrits for skill ID: {}. New value: {}", skillId, skill.getNbInscrits());
    }

    @Transactional
    public void decrementInscrits(Integer skillId, Jwt jwt) {
        UserResponse user = userServiceClient.getUserByKeycloakId(jwt.getSubject(), "Bearer " + jwt.getTokenValue());
        if (!user.roles().contains("PRODUCER")) {
            throw new AccessDeniedException("Only producers can decrement registrations");
        }
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new SkillNotFoundException("Skill not found with ID: " + skillId));
        if (skill.getNbInscrits() > 0) {
            skill.setNbInscrits(skill.getNbInscrits() - 1);
            skillRepository.save(skill);
            log.info("Decremented nbInscrits for skill ID: {}. New value: {}", skillId, skill.getNbInscrits());
        } else {
            throw new IllegalStateException("Cannot decrement nbInscrits below 0");
        }
    }

}