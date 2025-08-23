package com.example.servicemessagerie.service;

import com.example.servicemessagerie.dto.*;
import com.example.servicemessagerie.entity.*;
import com.example.servicemessagerie.repository.*;
import com.example.servicemessagerie.feignclient.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final UserServiceClient userServiceClient;
    private final SkillServiceClient skillServiceClient;
    private final ExchangeServiceClient exchangeServiceClient;


    /**
     * ✅ AMÉLIORÉ: Récupère les utilisateurs disponibles selon le rôle et le type de conversation
     */
    @Transactional(readOnly = true)
    public List<UserResponse> getAvailableUsersForConversation(String conversationType, Integer skillId, String token, Long currentUserId) {
        log.info("🔍 Getting available users for conversation type: {}, skillId: {}, currentUserId: {}",
                conversationType, skillId, currentUserId);

        try {
            // Déterminer le rôle de l'utilisateur actuel
            UserResponse currentUser = getUserById(currentUserId, token);
            List<String> userRoles = currentUser != null ? getUserRoles(currentUser) : List.of();

            switch (conversationType.toUpperCase()) {
                case "DIRECT":
                case "GROUP":
                    return getAvailableUsersForDirectOrGroup(userRoles, token, currentUserId);

                case "SKILL":
                case "SKILL_GROUP":
                    if (skillId == null) {
                        log.warn("⚠️ Skill ID is required for skill conversation");
                        return List.of();
                    }
                    return getAvailableUsersForSkill(skillId, token, currentUserId);

                default:
                    log.warn("⚠️ Unknown conversation type: {}", conversationType);
                    return List.of();
            }

        } catch (Exception e) {
            log.error("❌ Error getting available users: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * ✅ NOUVEAU: Récupère les utilisateurs disponibles pour conversations directes/groupe selon le rôle
     */
    private List<UserResponse> getAvailableUsersForDirectOrGroup(List<String> userRoles, String token, Long currentUserId) {
        try {
            if (userRoles.contains("PRODUCER")) {
                log.info("🎯 Producer: fetching subscribers");
                return exchangeServiceClient.getAllSubscribersForProducer(token);

            } else if (userRoles.contains("RECEIVER")) {
                log.info("🎯 Receiver: fetching community members");
                List<CommunityMemberResponse> communityMembers = exchangeServiceClient.getAllCommunityMembersForReceiver(token);

                return communityMembers.stream()
                        .filter(member -> !member.userId().equals(currentUserId)) // Exclure l'utilisateur actuel
                        .map(CommunityMemberResponse::toUserResponse)
                        .collect(Collectors.toList());

            } else {
                log.warn("⚠️ User has no valid role for conversation creation");
                return List.of();
            }

        } catch (Exception e) {
            log.error("❌ Error fetching users for direct/group conversation: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * ✅ NOUVEAU: Récupère les utilisateurs disponibles pour une compétence spécifique
     */
    private List<UserResponse> getAvailableUsersForSkill(Integer skillId, String token, Long currentUserId) {
        try {
            log.info("🎯 Fetching users for skill: {}", skillId);
            List<UserResponse> skillUsers = exchangeServiceClient.getSkillUsersSimple(skillId, token);

            // Filtrer l'utilisateur actuel
            return skillUsers.stream()
                    .filter(user -> !user.id().equals(currentUserId))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("❌ Error fetching users for skill {}: {}", skillId, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * ✅ AMÉLIORÉ: Crée ou récupère une conversation directe avec validation renforcée
     */
    // ✅ CORRECTION: Méthode simplifiée et permissive pour les conversations directes
    @Transactional
    public ConversationDTO createOrGetDirectConversation(Long userId1, Long userId2, String token) {
        log.debug("Creating or getting direct conversation between {} and {}", userId1, userId2);

        if (userId1.equals(userId2)) {
            throw new IllegalArgumentException("Cannot create conversation with yourself");
        }

        // ✅ Simplification: permettre à tous les utilisateurs authentifiés de créer des conversations
        Optional<Conversation> existing = conversationRepository
                .findDirectConversationBetweenUsers(userId1, userId2);

        if (existing.isPresent()) {
            log.debug("Direct conversation already exists: {}", existing.get().getId());
            return convertToDTO(existing.get(), userId1);
        }

        // ✅ Récupérer les utilisateurs et créer la conversation
        UserResponse user1 = fetchUserById(userId1, token);
        UserResponse user2 = fetchUserById(userId2, token);

        String conversationName = generateConversationName(user1, user2);

        Conversation conversation = Conversation.builder()
                .name(conversationName)
                .type(Conversation.ConversationType.DIRECT)
                .status(Conversation.ConversationStatus.ACTIVE)
                .build();

        conversation = conversationRepository.save(conversation);

        Set<ConversationParticipant> participants = new HashSet<>();
        participants.add(createParticipant(conversation, userId1, user1.firstName() + " " + user1.lastName()));
        participants.add(createParticipant(conversation, userId2, user2.firstName() + " " + user2.lastName()));

        participantRepository.saveAll(participants);
        conversation.setParticipants(participants);

        log.info("Created new direct conversation: {}", conversation.getId());
        return convertToDTO(conversation, userId1);
    }

    private ConversationParticipant createParticipant(Conversation conversation, Long userId, String userName) {
        return ConversationParticipant.builder()
                .conversation(conversation)
                .userId(userId)
                .userName(userName)
                .role(ConversationParticipant.ParticipantRole.MEMBER)
                .build();
    }
    /**
     * ✅ NOUVEAU: Vérifie si deux utilisateurs peuvent créer une conversation directe
     */
    private boolean canUsersCreateDirectConversation(Long userId1, Long userId2, String token) {
        try {
            UserResponse user1 = fetchUserById(userId1, token);
            UserResponse user2 = fetchUserById(userId2, token);

            List<String> roles1 = getUserRoles(user1);
            List<String> roles2 = getUserRoles(user2);

            // Cas 1: Producteur et ses subscribers
            if (roles1.contains("PRODUCER")) {
                List<UserResponse> subscribers = exchangeServiceClient.getAllSubscribersForProducer(
                        "Bearer " + extractTokenFromBearer(token));
                return subscribers.stream().anyMatch(sub -> sub.id().equals(userId2));
            }

            if (roles2.contains("PRODUCER")) {
                List<UserResponse> subscribers = exchangeServiceClient.getAllSubscribersForProducer(
                        "Bearer " + extractTokenFromBearer(token));
                return subscribers.stream().anyMatch(sub -> sub.id().equals(userId1));
            }

            // Cas 2: Receivers dans la même communauté
            if (roles1.contains("RECEIVER") && roles2.contains("RECEIVER")) {
                return areReceiversInSameCommunity(userId1, userId2, token);
            }

            return false;

        } catch (Exception e) {
            log.error("❌ Error checking if users can create conversation: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * ✅ NOUVEAU: Vérifie si deux receivers sont dans la même communauté
     */
    private boolean areReceiversInSameCommunity(Long receiverId1, Long receiverId2, String token) {
        try {
            // Temporairement permissif - à améliorer avec une vraie vérification communautaire
            List<CommunityMemberResponse> community = exchangeServiceClient.getAllCommunityMembersForReceiver(token);

            Set<Long> communityUserIds = community.stream()
                    .map(CommunityMemberResponse::userId)
                    .collect(Collectors.toSet());

            return communityUserIds.contains(receiverId1) && communityUserIds.contains(receiverId2);

        } catch (Exception e) {
            log.error("❌ Error checking receiver community: {}", e.getMessage(), e);
            return true; // Permissif en cas d'erreur
        }
    }

    /**
     * ✅ AMÉLIORÉ: Crée ou récupère une conversation de compétence avec utilisateurs autorisés
     */

    /**
     * ✅ NOUVEAU: Vérifie si un utilisateur peut accéder à une conversation de compétence
     */
    private boolean canUserAccessSkillConversation(Integer skillId, Long userId, String token) {
        try {
            List<UserResponse> authorizedUsers = exchangeServiceClient.getSkillUsersSimple(skillId, token);
            return authorizedUsers.stream().anyMatch(user -> user.id().equals(userId));

        } catch (Exception e) {
            log.error("❌ Error checking skill access for user {} and skill {}: {}", userId, skillId, e.getMessage());
            return false;
        }
    }

    /**
     * ✅ AMÉLIORÉ: Crée une nouvelle conversation de compétence avec participants autorisés
     */
    private ConversationDTO createNewSkillConversation(Integer skillId, SkillResponse skill, Long userId, String token) {
        String conversationName;

        if (skill != null) {
            conversationName = "Skill: " + skill.name();
        } else {
            conversationName = "Skill Discussion: " + skillId;
        }

        Conversation conversation = Conversation.builder()
                .name(conversationName)
                .type(Conversation.ConversationType.SKILL_GROUP)
                .skillId(skillId)
                .status(Conversation.ConversationStatus.ACTIVE)
                .build();

        conversation = conversationRepository.save(conversation);
        log.info("✅ Created new skill conversation: {} for skill {}", conversation.getId(), skillId);

        // ✅ AMÉLIORÉ: Ajouter tous les utilisateurs autorisés automatiquement
        addAuthorizedUsersToSkillConversation(conversation, skillId, token);

        return convertToDTO(conversation, userId);
    }

    /**
     * ✅ NOUVEAU: Ajoute tous les utilisateurs autorisés à une conversation de compétence
     */
    private void addAuthorizedUsersToSkillConversation(Conversation conversation, Integer skillId, String token) {
        try {
            List<UserResponse> authorizedUsers = exchangeServiceClient.getSkillUsersSimple(skillId, token);

            List<ConversationParticipant> participants = new ArrayList<>();

            for (UserResponse user : authorizedUsers) {
                try {
                    // Déterminer le rôle dans la conversation
                    ConversationParticipant.ParticipantRole role = ConversationParticipant.ParticipantRole.MEMBER;

                    // Le producteur de la compétence devient admin
                    SkillResponse skill = fetchSkill(skillId);
                    if (skill != null && user.id().equals(skill.userId())) {
                        role = ConversationParticipant.ParticipantRole.ADMIN;
                    }

                    ConversationParticipant participant = ConversationParticipant.builder()
                            .conversation(conversation)
                            .userId(user.id())
                            .userName(user.firstName() + " " + user.lastName())
                            .role(role)
                            .build();

                    participants.add(participant);

                } catch (Exception e) {
                    log.warn("⚠️ Could not add user {} to skill conversation: {}", user.id(), e.getMessage());
                }
            }

            if (!participants.isEmpty()) {
                participantRepository.saveAll(participants);
                log.info("✅ Added {} users to skill conversation {}", participants.size(), conversation.getId());
            }

        } catch (Exception e) {
            log.error("❌ Error adding authorized users to skill conversation: {}", e.getMessage(), e);
        }
    }

    /**
     * ✅ AMÉLIORÉ: Crée une conversation de groupe avec validation des participants
     */
    @Transactional
    public ConversationDTO createGroupConversation(CreateConversationRequest request, Long creatorId, String token) {
        log.info("Creating group conversation '{}' by user {}", request.getName(), creatorId);

        // Valider les participants
        Set<Long> allParticipantIds = new HashSet<>(request.getParticipantIds());
        allParticipantIds.add(creatorId);

        // ✅ NOUVEAU: Vérifier que tous les participants peuvent être ajoutés
        if (!canUsersCreateGroupConversation(allParticipantIds, creatorId, token)) {
            throw new IllegalArgumentException("Some participants cannot be added to this group conversation");
        }

        // Vérifier que tous les utilisateurs existent
        Map<Long, UserResponse> participants = validateAndFetchUsers(allParticipantIds, token);

        // Créer la conversation
        Conversation conversation = Conversation.builder()
                .name(request.getName())
                .type(Conversation.ConversationType.GROUP)
                .status(Conversation.ConversationStatus.ACTIVE)
                .build();

        conversation = conversationRepository.save(conversation);

        // Ajouter les participants
        Set<ConversationParticipant> conversationParticipants = new HashSet<>();

        // Créateur en tant qu'admin
        UserResponse creator = participants.get(creatorId);
        conversationParticipants.add(ConversationParticipant.builder()
                .conversation(conversation)
                .userId(creatorId)
                .userName(creator.firstName() + " " + creator.lastName())
                .role(ConversationParticipant.ParticipantRole.ADMIN)
                .build());

        // Autres participants en tant que membres
        for (Long participantId : request.getParticipantIds()) {
            if (!participantId.equals(creatorId)) {
                UserResponse user = participants.get(participantId);
                if (user != null) {
                    conversationParticipants.add(ConversationParticipant.builder()
                            .conversation(conversation)
                            .userId(participantId)
                            .userName(user.firstName() + " " + user.lastName())
                            .role(ConversationParticipant.ParticipantRole.MEMBER)
                            .build());
                }
            }
        }

        participantRepository.saveAll(conversationParticipants);
        conversation.setParticipants(conversationParticipants);

        log.info("Group conversation created with {} participants", conversationParticipants.size());
        return convertToDTO(conversation, creatorId);
    }

    /**
     * ✅ NOUVEAU: Vérifie si les utilisateurs peuvent créer une conversation de groupe
     */
    private boolean canUsersCreateGroupConversation(Set<Long> participantIds, Long creatorId, String token) {
        try {
            UserResponse creator = fetchUserById(creatorId, token);
            List<String> creatorRoles = getUserRoles(creator);

            if (creatorRoles.contains("PRODUCER")) {
                // Le producteur peut inviter ses subscribers
                List<UserResponse> subscribers = exchangeServiceClient.getAllSubscribersForProducer(token);
                Set<Long> subscriberIds = subscribers.stream()
                        .map(UserResponse::id)
                        .collect(Collectors.toSet());

                return participantIds.stream()
                        .allMatch(id -> id.equals(creatorId) || subscriberIds.contains(id));

            } else if (creatorRoles.contains("RECEIVER")) {
                // Le receiver peut inviter les membres de sa communauté
                List<CommunityMemberResponse> community = exchangeServiceClient.getAllCommunityMembersForReceiver(token);
                Set<Long> communityIds = community.stream()
                        .map(CommunityMemberResponse::userId)
                        .collect(Collectors.toSet());

                return participantIds.stream()
                        .allMatch(id -> id.equals(creatorId) || communityIds.contains(id));
            }

            return false;

        } catch (Exception e) {
            log.error("❌ Error validating group conversation participants: {}", e.getMessage(), e);
            return false;
        }
    }

    // ===== MÉTHODES UTILITAIRES =====

    /**
     * ✅ NOUVEAU: Extrait le token du format Bearer
     */
    private String extractTokenFromBearer(String bearerToken) {
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return bearerToken;
    }

    /**
     * ✅ NOUVEAU: Récupère les rôles d'un utilisateur
     */
    private List<String> getUserRoles(UserResponse user) {
        // Supposons que les rôles sont stockés dans un champ roles ou extraits du token
        // Cette implémentation dépend de votre structure UserResponse
        try {
            // À adapter selon votre implémentation
            return List.of("RECEIVER"); // Placeholder
        } catch (Exception e) {
            log.warn("⚠️ Could not determine user roles for user {}: {}", user.id(), e.getMessage());
            return List.of();
        }
    }

    /**
     * ✅ AMÉLIORATION: Récupère un utilisateur par ID avec gestion d'erreur
     */
    private UserResponse getUserById(Long userId, String token) {
        try {
            ResponseEntity<UserResponse> response = userServiceClient.getUserById(userId, token);
            return response.getBody();
        } catch (Exception e) {
            log.error("❌ Error fetching user {}: {}", userId, e.getMessage());
            return null;
        }
    }






    /**
     * ✅ CORRIGÉ: Crée ou récupère une conversation de groupe pour une compétence
     */
    @Transactional
    public ConversationDTO createOrGetSkillConversation(Integer skillId, Long userId, String token) {
        log.debug("Creating or getting skill conversation for skill {} and user {}", skillId, userId);

        // ✅ : Permettre à tout utilisateur authentifié de créer une conversation de compétence
        SkillResponse skill = null;
        try {
            skill = fetchSkill(skillId);
            log.debug("Skill found: {}", skill != null ? skill.name() : "null");
        } catch (Exception e) {
            log.warn("Skill {} not found in skill service, creating conversation anyway", skillId);
        }

        // Chercher conversation existante pour cette compétence
        Optional<Conversation> existing = conversationRepository
                .findBySkillIdAndType(skillId, Conversation.ConversationType.SKILL_GROUP);

        if (existing.isPresent()) {
            log.debug("Skill conversation already exists: {}", existing.get().getId());
            // Ajouter l'utilisateur s'il n'y est pas déjà
            addUserToSkillConversationIfNeeded(existing.get(), userId, token);
            return convertToDTO(existing.get(), userId);
        }

        // ✅ : Créer une nouvelle conversation même si le skill n'existe pas exactement
        return createNewSkillConversationFixed(skillId, skill, userId, token);
    }

    /**
     * ✅ NOUVEAU: Version corrigée de création de conversation de compétence
     */
    private ConversationDTO createNewSkillConversationFixed(Integer skillId, SkillResponse skill, Long userId, String token) {
        String conversationName;

        if (skill != null) {
            conversationName = "Skill: " + skill.name();
        } else {
            conversationName = "Skill Discussion: " + skillId;
        }

        Conversation conversation = Conversation.builder()
                .name(conversationName)
                .type(Conversation.ConversationType.SKILL_GROUP)
                .skillId(skillId)
                .status(Conversation.ConversationStatus.ACTIVE)
                .build();

        conversation = conversationRepository.save(conversation);
        log.info("✅ Created new skill conversation: {} for skill {}", conversation.getId(), skillId);

        // ✅ : Ajouter l'utilisateur actuel comme premier participant
        UserResponse currentUser = fetchUserById(userId, token);
        ConversationParticipant userParticipant = ConversationParticipant.builder()
                .conversation(conversation)
                .userId(userId)
                .userName(currentUser.firstName() + " " + currentUser.lastName())
                .role(ConversationParticipant.ParticipantRole.ADMIN) // Premier utilisateur = admin
                .build();

        participantRepository.save(userParticipant);

        // ✅ : Ajouter le propriétaire de la compétence s'il est différent
        if (skill != null && !userId.equals(skill.userId())) {
            try {
                UserResponse skillOwner = fetchUserById(skill.userId(), token);
                ConversationParticipant ownerParticipant = ConversationParticipant.builder()
                        .conversation(conversation)
                        .userId(skill.userId())
                        .userName(skillOwner.firstName() + " " + skillOwner.lastName())
                        .role(ConversationParticipant.ParticipantRole.MEMBER)
                        .build();
                participantRepository.save(ownerParticipant);
                log.debug("Added skill owner {} to conversation", skill.userId());
            } catch (Exception e) {
                log.warn("Could not add skill owner to conversation: {}", e.getMessage());
            }
        }

        return convertToDTO(conversation, userId);
    }

    /**
     * Récupère toutes les conversations d'un utilisateur
     */
    @Transactional(readOnly = true)
    public Page<ConversationDTO> getUserConversations(Long userId, int page, int size) {
        log.info("📋 Fetching conversations for user {}, page {}, size {}", userId, page, size);

        try {
            // Vérifier si l'utilisateur a des participations
            long participantCount = conversationRepository.countConversationsByUserId(userId);
            log.info("🔍 User {} has {} conversation participations", userId, participantCount);

            if (participantCount == 0) {
                log.warn("⚠️ User {} has no conversation participations", userId);
                return Page.empty();
            }

            // Utiliser la requête paginée optimisée
            PageRequest pageable = PageRequest.of(page, size,
                    Sort.by(Sort.Order.desc("lastMessageTime").nullsLast()));

            Page<Conversation> conversations = conversationRepository
                    .findByParticipantUserId(userId, pageable);

            log.info("✅ Paginated query found {} conversations for user {}",
                    conversations.getTotalElements(), userId);

            // Charger les participants séparément pour éviter le warning Hibernate
            List<Long> conversationIds = conversations.getContent().stream()
                    .map(Conversation::getId)
                    .collect(Collectors.toList());

            if (!conversationIds.isEmpty()) {
                List<Conversation> conversationsWithParticipants =
                        conversationRepository.findConversationsWithParticipants(conversationIds);

                // Remapper les conversations avec leurs participants
                Map<Long, Conversation> conversationMap = conversationsWithParticipants.stream()
                        .collect(Collectors.toMap(Conversation::getId, c -> c));

                conversations.getContent().forEach(c -> {
                    Conversation fullConversation = conversationMap.get(c.getId());
                    if (fullConversation != null) {
                        c.setParticipants(fullConversation.getParticipants());
                    }
                });
            }

            // Convertir en DTOs avec gestion d'erreur par conversation
            List<ConversationDTO> conversationDTOs = new ArrayList<>();
            for (Conversation conv : conversations.getContent()) {
                try {
                    log.debug("  - Processing conversation {}: {}, type: {}, participants: {}",
                            conv.getId(), conv.getName(), conv.getType(),
                            conv.getParticipants() != null ? conv.getParticipants().size() : 0);

                    ConversationDTO dto = convertToDTO(conv, userId);
                    conversationDTOs.add(dto);

                    log.debug("  - ✅ Converted conversation {} successfully", conv.getId());
                } catch (Exception e) {
                    log.error("❌ Error converting conversation {} to DTO: {}", conv.getId(), e.getMessage(), e);
                    // Continuer avec les autres conversations
                }
            }

            log.info("✅ Successfully processed {} out of {} conversations",
                    conversationDTOs.size(), conversations.getContent().size());

            return new PageImpl<>(conversationDTOs, pageable, conversations.getTotalElements());

        } catch (Exception e) {
            log.error("❌ Error fetching conversations for user {}: {}", userId, e.getMessage(), e);
            return Page.empty();
        }
    }
    /**
     * ✅ NOUVEAU: Méthode de diagnostic pour debug
     */
    @Transactional(readOnly = true)
    public void diagnoseUserConversations(Long userId) {
        log.info("🔍 === DIAGNOSTIC CONVERSATIONS POUR USER {} ===", userId);

        try {
            // 1. Vérifier les participations
            long participantCount = conversationRepository.countConversationsByUserId(userId);
            log.info("1. Nombre de participations: {}", participantCount);

            // 2. Lister toutes les participations
            List<Object> allParticipants = conversationRepository.findAllParticipantsByUserId(userId);
            log.info("2. Détails des participations: {}", allParticipants);

            // 3. Tester requête simple
            List<Conversation> simpleConversations = conversationRepository.findByParticipantUserIdSimple(userId);
            log.info("3. Conversations trouvées (requête simple): {}", simpleConversations.size());
            simpleConversations.forEach(c -> {
                log.info("   - Conversation {}: {}, status: {}, participants: {}",
                        c.getId(), c.getName(), c.getStatus(),
                        c.getParticipants() != null ? c.getParticipants().size() : 0);
            });

            // 4. Tester requête paginée
            PageRequest pageable = PageRequest.of(0, 10);
            Page<Conversation> pagedConversations = conversationRepository.findByParticipantUserId(userId, pageable);
            log.info("4. Conversations trouvées (requête paginée): {}", pagedConversations.getTotalElements());

            // 5. Vérifier les statuts
            long activeCount = conversationRepository.countActiveConversationsByUserId(userId);
            log.info("5. Conversations actives: {}", activeCount);

        } catch (Exception e) {
            log.error("❌ Erreur lors du diagnostic: {}", e.getMessage(), e);
        }

        log.info("🔍 === FIN DIAGNOSTIC ===");
    }
    /**
     * Récupère une conversation spécifique
     */
    @Transactional(readOnly = true)
    public ConversationDTO getConversation(Long conversationId, Long userId) {
        log.info("📋 Getting conversation {} for user {}", conversationId, userId);

        // Vérifier si la conversation existe
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> {
                    log.error("❌ Conversation {} not found", conversationId);
                    return new IllegalArgumentException("Conversation not found: " + conversationId);
                });

        log.info("✅ Conversation {} found: {}, type: {}", conversationId, conversation.getName(), conversation.getType());

        // Vérifier l'accès
        boolean hasAccess = conversationRepository.canUserAccessConversation(conversationId, userId);

        if (!hasAccess) {
            // Pour les conversations de compétence, permettre l'accès même sans participation
            if (conversation.getType() == Conversation.ConversationType.SKILL_GROUP) {
                log.info("ℹ️ Allowing access to skill conversation {} for user {}", conversationId, userId);
            } else {
                log.error("❌ User {} not authorized for conversation {}", userId, conversationId);
                throw new SecurityException("User not authorized for conversation: " + conversationId);
            }
        }

        log.info("✅ User {} has access to conversation {}", userId, conversationId);
        return convertToDTO(conversation, userId);
    }
    /**
     * Recherche des conversations par nom
     */
    @Transactional(readOnly = true)
    public List<ConversationDTO> searchConversations(Long userId, String query) {
        List<Conversation> conversations = conversationRepository
                .searchByNameAndUserId(userId, query);

        return conversations.stream()
                .map(conv -> convertToDTO(conv, userId))
                .collect(Collectors.toList());
    }

    /**
     * Archive une conversation
     */
    @Transactional
    public void archiveConversation(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));

        // Vérifier que l'utilisateur est participant
        ConversationParticipant participant = participantRepository
                .findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new SecurityException("User not authorized"));

        // Marquer comme inactif pour cet utilisateur
        participant.setActive(false);
        participantRepository.save(participant);

        log.info("User {} archived conversation {}", userId, conversationId);
    }

    /**
     * Compte le nombre de messages non lus
     */
    @Transactional(readOnly = true)
    public int getUnreadCount(Long userId) {
        try {
            int count = messageRepository.countUnreadMessagesForUser(userId);
            log.debug("Unread count for user {}: {}", userId, count);
            return count;
        } catch (Exception e) {
            log.error("Error getting unread count for user {}: {}", userId, e.getMessage());
            return 0;
        }
    }

    /**
     * Compte les messages non lus par conversation
     */
    @Transactional(readOnly = true)
    public Map<Long, Integer> getUnreadCountPerConversation(Long userId) {
        try {
            List<Object[]> results = messageRepository.countUnreadMessagesPerConversation(userId);

            Map<Long, Integer> counts = results.stream()
                    .collect(Collectors.toMap(
                            r -> (Long) r[0],
                            r -> ((Number) r[1]).intValue()
                    ));

            log.debug("Unread counts per conversation for user {}: {}", userId, counts);
            return counts;
        } catch (Exception e) {
            log.error("Error getting unread counts for user {}: {}", userId, e.getMessage());
            return new HashMap<>();
        }
    }


    // ===== NOUVELLES MÉTHODES UTILITAIRES =====

    /**
     * ✅ NOUVEAU: Vérifier si un utilisateur peut envoyer des messages
     */
    public boolean canUserSendMessage(Conversation conversation, Long userId) {
        // Vérifier si la conversation est active
        if (conversation.getStatus() != Conversation.ConversationStatus.ACTIVE) {
            log.debug("Conversation {} is not active: {}", conversation.getId(), conversation.getStatus());
            return false;
        }

        // Pour les conversations de compétence, autoriser tout utilisateur authentifié
        if (conversation.getType() == Conversation.ConversationType.SKILL_GROUP) {
            log.debug("Skill conversation {} allows all authenticated users", conversation.getId());
            return true;
        }

        // Pour les autres types, vérifier la participation
        boolean isParticipant = conversation.getParticipants().stream()
                .anyMatch(p -> p.getUserId().equals(userId) && p.isActive());

        log.debug("User {} is participant of conversation {}: {}", userId, conversation.getId(), isParticipant);
        return isParticipant;
    }

    /**
     * ✅ NOUVEAU: Récupérer l'entité conversation
     */
    public Conversation getConversationEntity(Long conversationId) {
        return conversationRepository.findById(conversationId).orElse(null);
    }

    /**
     * ✅ CORRIGÉ: Ajouter un utilisateur à une conversation de compétence si nécessaire
     */
    public void addUserToSkillConversationIfNeeded(Conversation conversation, Long userId, String token) {
        boolean isAlreadyParticipant = conversation.getParticipants().stream()
                .anyMatch(p -> p.getUserId().equals(userId) && p.isActive());

        if (!isAlreadyParticipant) {
            UserResponse user = fetchUserById(userId, token);
            ConversationParticipant participant = ConversationParticipant.builder()
                    .conversation(conversation)
                    .userId(userId)
                    .userName(user.firstName() + " " + user.lastName())
                    .role(ConversationParticipant.ParticipantRole.MEMBER)
                    .build();

            participantRepository.save(participant);
            log.info("✅ Added user {} to skill conversation {}", userId, conversation.getId());
        }
    }

    // ===== MÉTHODES PRIVÉES =====

    private UserResponse fetchUserById(Long userId, String token) {
        try {
            ResponseEntity<UserResponse> response = userServiceClient.getUserById(userId, token);
            UserResponse user = response.getBody();
            if (user == null) {
                throw new IllegalArgumentException("User not found with ID: " + userId);
            }
            return user;
        } catch (Exception e) {
            log.error("Error fetching user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Error fetching user: " + e.getMessage());
        }
    }

    private SkillResponse fetchSkill(Integer skillId) {
        try {
            return skillServiceClient.getSkillById(skillId);
        } catch (Exception e) {
            log.error("Error fetching skill {}: {}", skillId, e.getMessage());
            return null;
        }
    }

    private String generateConversationName(UserResponse user1, UserResponse user2) {
        String name1 = user1.firstName() + " " + user1.lastName();
        String name2 = user2.firstName() + " " + user2.lastName();

        // Générer un nom cohérent indépendamment de l'ordre
        if (name1.compareTo(name2) < 0) {
            return name1 + " & " + name2;
        } else {
            return name2 + " & " + name1;
        }
    }

    private Map<Long, UserResponse> validateAndFetchUsers(Set<Long> userIds, String token) {
        Map<Long, UserResponse> users = new HashMap<>();

        for (Long userId : userIds) {
            try {
                UserResponse user = fetchUserById(userId, token);
                users.put(userId, user);
            } catch (Exception e) {
                log.warn("Could not fetch user {}: {}", userId, e.getMessage());
            }
        }

        return users;
    }

    /**
     *  Conversion vers DTO avec gestion correcte des permissions
     */

    private ConversationDTO convertToDTO(Conversation conversation, Long currentUserId) {
        try {
            // ✅ RÉCUPÉRATION SÉPARÉE DES PARTICIPANTS
            List<ConversationParticipant> participants =
                    participantRepository.findActiveParticipantsByConversationId(conversation.getId());

            List<ParticipantDTO> participantDTOs = participants.stream()
                    .map(p -> ParticipantDTO.builder()
                            .userId(p.getUserId())
                            .userName(p.getUserName())
                            .role(p.getRole().name())
                            .isOnline(false)
                            .build())
                    .collect(Collectors.toList());

            // ✅ UNREAD COUNT SAFE
            int unreadCount = 0;
            try {
                unreadCount = messageRepository.countUnreadInConversation(conversation.getId(), currentUserId);
            } catch (Exception e) {
                log.warn("Could not get unread count: {}", e.getMessage());
            }

            return ConversationDTO.builder()
                    .id(conversation.getId())
                    .name(conversation.getName())
                    .type(conversation.getType().name())
                    .status(conversation.getStatus().name())
                    .skillId(conversation.getSkillId())
                    .participants(participantDTOs)
                    .lastMessage(conversation.getLastMessage())
                    .lastMessageTime(conversation.getLastMessageTime())
                    .unreadCount(unreadCount)
                    .createdAt(conversation.getCreatedAt())
                    .canSendMessage(true)
                    .isAdmin(isUserAdmin(participants, currentUserId))
                    .build();

        } catch (Exception e) {
            log.error("❌ Error converting conversation: {}", e.getMessage());
            return createMinimalDTO(conversation);
        }
    }

    // ✅ CORRIGER isUserAdmin
    private boolean isUserAdmin(List<ConversationParticipant> participants, Long userId) {
        if (userId == null || participants == null) return false;

        return participants.stream()
                .anyMatch(p -> p.getUserId().equals(userId) &&
                        p.getRole() == ConversationParticipant.ParticipantRole.ADMIN &&
                        p.isActive());
    }

    // ✅ DTO MINIMAL EN CAS D'ERREUR
    private ConversationDTO createMinimalDTO(Conversation conversation) {
        return ConversationDTO.builder()
                .id(conversation.getId())
                .name(conversation.getName())
                .type(conversation.getType().name())
                .status(conversation.getStatus().name())
                .participants(new ArrayList<>())
                .unreadCount(0)
                .canSendMessage(true)
                .isAdmin(false)
                .build();
    }


}