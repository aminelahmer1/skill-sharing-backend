package com.example.servicemessagerie.service;

import com.example.servicemessagerie.dto.*;
import com.example.servicemessagerie.entity.*;
import com.example.servicemessagerie.repository.*;
import com.example.servicemessagerie.feignclient.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
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

    /**
     * Crée ou récupère une conversation directe entre deux utilisateurs
     * Permet toutes les interactions : Producer↔Receiver, Receiver↔Receiver, Producer↔Producer
     */
    @Transactional
    public ConversationDTO createOrGetDirectConversation(Long userId1, Long userId2, String token) {
        log.debug("Creating or getting direct conversation between {} and {}", userId1, userId2);

        // Validation de base
        if (userId1.equals(userId2)) {
            throw new IllegalArgumentException("Cannot create conversation with yourself");
        }

        // Vérifier si une conversation directe existe déjà
        Optional<Conversation> existing = conversationRepository
                .findDirectConversationBetweenUsers(userId1, userId2);

        if (existing.isPresent()) {
            log.debug("Direct conversation already exists: {}", existing.get().getId());
            return convertToDTO(existing.get(), userId1);
        }

        // Récupérer les informations des utilisateurs
        UserResponse user1 = fetchUserById(userId1, token);
        UserResponse user2 = fetchUserById(userId2, token);

        // Créer le nom de la conversation
        String conversationName = generateConversationName(user1, user2);

        // Créer une nouvelle conversation
        Conversation conversation = Conversation.builder()
                .name(conversationName)
                .type(Conversation.ConversationType.DIRECT)
                .status(Conversation.ConversationStatus.ACTIVE)
                .build();

        conversation = conversationRepository.save(conversation);
        log.info("Created new direct conversation: {}", conversation.getId());

        // Ajouter les participants
        Set<ConversationParticipant> participants = new HashSet<>();

        participants.add(ConversationParticipant.builder()
                .conversation(conversation)
                .userId(userId1)
                .userName(user1.firstName() + " " + user1.lastName())
                .role(ConversationParticipant.ParticipantRole.MEMBER)
                .build());

        participants.add(ConversationParticipant.builder()
                .conversation(conversation)
                .userId(userId2)
                .userName(user2.firstName() + " " + user2.lastName())
                .role(ConversationParticipant.ParticipantRole.MEMBER)
                .build());

        participantRepository.saveAll(participants);
        conversation.setParticipants(participants);

        return convertToDTO(conversation, userId1);
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
        log.debug("Fetching conversations for user {}, page {}, size {}", userId, page, size);

        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "lastMessageTime"));

        Page<Conversation> conversations = conversationRepository
                .findByParticipantUserIdAndStatus(userId,
                        Conversation.ConversationStatus.ACTIVE, pageable);

        log.debug("Found {} conversations for user {}", conversations.getTotalElements(), userId);

        return conversations.map(conv -> convertToDTO(conv, userId));
    }

    /**
     * Récupère une conversation spécifique
     */
    @Transactional(readOnly = true)
    public ConversationDTO getConversation(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));

        // Vérifier l'accès
        boolean hasAccess = conversation.getParticipants().stream()
                .anyMatch(p -> p.getUserId().equals(userId) && p.isActive());

        if (!hasAccess) {
            throw new SecurityException("User not authorized for conversation: " + conversationId);
        }

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

    /**
     * Crée une conversation de groupe
     */
    @Transactional
    public ConversationDTO createGroupConversation(CreateConversationRequest request, Long creatorId, String token) {
        log.info("Creating group conversation '{}' by user {}", request.getName(), creatorId);

        // Valider les participants
        Set<Long> allParticipantIds = new HashSet<>(request.getParticipantIds());
        allParticipantIds.add(creatorId);

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
        // Compter les messages non lus
        int unreadCount = 0;
        if (currentUserId != null) {
            try {
                unreadCount = messageRepository.countUnreadInConversation(conversation.getId(), currentUserId);
            } catch (Exception e) {
                log.warn("Could not get unread count for conversation {}: {}",
                        conversation.getId(), e.getMessage());
            }
        }

        // Récupérer le dernier message
        Message lastMessage = messageRepository
                .findTopByConversationIdOrderBySentAtDesc(conversation.getId())
                .orElse(null);

        // Créer la liste des participants
        List<ParticipantDTO> participants = conversation.getParticipants().stream()
                .filter(ConversationParticipant::isActive)
                .map(p -> ParticipantDTO.builder()
                        .userId(p.getUserId())
                        .userName(p.getUserName())
                        .role(p.getRole().name())
                        .isOnline(false) // TODO: Implémenter le service de présence
                        .build())
                .collect(Collectors.toList());

        // ✅ : Déterminer si l'utilisateur peut envoyer des messages
        boolean canSendMessage = true; // Par défaut, autorisé
        boolean isAdmin = false;

        if (currentUserId != null) {
            // Vérifier si l'utilisateur est participant actif
            Optional<ConversationParticipant> userParticipant = conversation.getParticipants().stream()
                    .filter(p -> p.getUserId().equals(currentUserId) && p.isActive())
                    .findFirst();

            if (userParticipant.isPresent()) {
                canSendMessage = true; // Participant actif peut envoyer
                isAdmin = userParticipant.get().getRole() == ConversationParticipant.ParticipantRole.ADMIN;
            } else {
                // ✅ : Si pas participant, permettre quand même l'envoi pour les conversations publiques
                if (conversation.getType() == Conversation.ConversationType.SKILL_GROUP) {
                    canSendMessage = true; // Les conversations de compétence sont ouvertes
                } else {
                    canSendMessage = false; // Conversations privées nécessitent d'être participant
                }
            }
        }

        //  Vérifier le statut de la conversation
        if (conversation.getStatus() != Conversation.ConversationStatus.ACTIVE) {
            canSendMessage = false;
        }

        log.debug("Conversation {}: canSendMessage={}, isAdmin={}, type={}, status={}",
                conversation.getId(), canSendMessage, isAdmin, conversation.getType(), conversation.getStatus());

        return ConversationDTO.builder()
                .id(conversation.getId())
                .name(conversation.getName())
                .type(conversation.getType().name())
                .status(conversation.getStatus().name())
                .skillId(conversation.getSkillId())
                .participants(participants)
                .lastMessage(lastMessage != null ? lastMessage.getContent() : null)
                .lastMessageTime(conversation.getLastMessageTime())
                .unreadCount(unreadCount)
                .createdAt(conversation.getCreatedAt())
                .canSendMessage(canSendMessage) // ✅ : Ajouter cette propriété
                .isAdmin(isAdmin) // ✅ : Ajouter cette propriété
                .build();
    }
}