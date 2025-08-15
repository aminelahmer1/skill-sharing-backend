package com.example.serviceexchange.service;

import com.example.serviceexchange.dto.*;
import com.example.serviceexchange.entity.Exchange;
import com.example.serviceexchange.exception.ExchangeNotFoundException;
import com.example.serviceexchange.repository.ExchangeRepository;
import com.example.serviceexchange.FeignClient.UserServiceClient;
import com.example.serviceexchange.FeignClient.SkillServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RatingService {

    private final ExchangeRepository exchangeRepository;
    private final UserServiceClient userServiceClient;
    private final SkillServiceClient skillServiceClient;
    private final NotificationService notificationService;

    /**
     * Soumettre ou mettre à jour un rating pour un échange
     */
    @Transactional
    public RatingResponse submitRating(Integer exchangeId, RatingRequest request, Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        UserResponse currentUser = getUserByKeycloakId(jwt.getSubject(), token);

        // Récupérer l'échange
        Exchange exchange = exchangeRepository.findById(exchangeId)
                .orElseThrow(() -> new ExchangeNotFoundException("Exchange not found"));

        // Vérifier que l'utilisateur est bien le receiver
        if (!exchange.getReceiverId().equals(currentUser.id())) {
            throw new AccessDeniedException("Only the receiver can rate this exchange");
        }

        // Vérifier que l'échange est complété
        if (!"COMPLETED".equals(exchange.getStatus())) {
            throw new IllegalStateException("Can only rate completed exchanges");
        }

        // Définir le rating
        exchange.setReceiverRating(request.rating(), request.comment());
        Exchange savedExchange = exchangeRepository.save(exchange);

        log.info("Rating submitted for exchange {}: {} stars", exchangeId, request.rating());

        // Notifier le producteur
        try {
            UserResponse producer = getUserById(exchange.getProducerId(), token);
            SkillResponse skill = getSkillById(exchange.getSkillId());

        } catch (Exception e) {
            log.error("Failed to send rating notification: {}", e.getMessage());
        }

        return new RatingResponse(
                savedExchange.getId(),
                savedExchange.getReceiverRating(),
                savedExchange.getReceiverComment(),
                savedExchange.getRatingDate(),
                currentUser.firstName() + " " + currentUser.lastName(),
                currentUser.id()
        );
    }

    /**
     * Récupérer le rating d'un échange spécifique
     */
    @Transactional(readOnly = true)
    public RatingResponse getRatingForExchange(Integer exchangeId, Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        UserResponse currentUser = getUserByKeycloakId(jwt.getSubject(), token);

        Exchange exchange = exchangeRepository.findById(exchangeId)
                .orElseThrow(() -> new ExchangeNotFoundException("Exchange not found"));

        // Vérifier que l'utilisateur est impliqué dans l'échange
        if (!exchange.getReceiverId().equals(currentUser.id()) &&
                !exchange.getProducerId().equals(currentUser.id())) {
            throw new AccessDeniedException("Not authorized to view this rating");
        }

        if (exchange.getReceiverRating() == null) {
            return null;
        }

        UserResponse receiver = getUserById(exchange.getReceiverId(), token);

        return new RatingResponse(
                exchange.getId(),
                exchange.getReceiverRating(),
                exchange.getReceiverComment(),
                exchange.getRatingDate(),
                receiver.firstName() + " " + receiver.lastName(),
                receiver.id()
        );
    }

    /**
     * Récupérer les statistiques de rating pour un producteur
     */
    @Transactional(readOnly = true)
    public ProducerRatingStats getProducerRatingStats(Long producerId, Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        UserResponse producer = getUserById(producerId, token);

        // Récupérer tous les échanges complétés
        List<Exchange> completedExchanges = exchangeRepository.findCompletedExchangesByProducerId(producerId);
        List<Exchange> ratedExchanges = completedExchanges.stream()
                .filter(e -> e.getReceiverRating() != null)
                .collect(Collectors.toList());

        // Calculer la moyenne
        Double averageRating = ratedExchanges.isEmpty() ? 0.0 :
                ratedExchanges.stream()
                        .mapToInt(Exchange::getReceiverRating)
                        .average()
                        .orElse(0.0);

        // Calculer la distribution des étoiles
        Map<Integer, Long> distribution = ratedExchanges.stream()
                .collect(Collectors.groupingBy(
                        Exchange::getReceiverRating,
                        Collectors.counting()
                ));

        List<RatingDistribution> ratingDistribution = new ArrayList<>();
        for (int stars = 1; stars <= 5; stars++) {
            long count = distribution.getOrDefault(stars, 0L);
            double percentage = ratedExchanges.isEmpty() ? 0.0 :
                    (count * 100.0) / ratedExchanges.size();
            ratingDistribution.add(new RatingDistribution(stars, (int) count, percentage));
        }

        // Récupérer les derniers ratings (max 10)
        List<RecentRating> recentRatings = ratedExchanges.stream()
                .sorted((e1, e2) -> e2.getRatingDate().compareTo(e1.getRatingDate()))
                .limit(10)
                .map(exchange -> {
                    try {
                        UserResponse receiver = getUserById(exchange.getReceiverId(), token);
                        SkillResponse skill = getSkillById(exchange.getSkillId());
                        return new RecentRating(
                                exchange.getId(),
                                skill != null ? skill.name() : "Compétence supprimée",
                                receiver.firstName() + " " + receiver.lastName(),
                                exchange.getReceiverRating(),
                                exchange.getReceiverComment(),
                                exchange.getRatingDate()
                        );
                    } catch (Exception e) {
                        log.error("Error loading rating details: {}", e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return new ProducerRatingStats(
                producerId,
                producer.firstName() + " " + producer.lastName(),
                Math.round(averageRating * 10.0) / 10.0, // Arrondir à 1 décimale
                ratedExchanges.size(),
                completedExchanges.size(),
                ratingDistribution,
                recentRatings
        );
    }

    /**
     * Récupérer les statistiques de rating pour une compétence
     */
    @Transactional(readOnly = true)
    public SkillRatingStats getSkillRatingStats(Integer skillId, Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        SkillResponse skill = getSkillById(skillId);

        if (skill == null) {
            throw new ExchangeNotFoundException("Skill not found");
        }

        List<Exchange> ratedExchanges = exchangeRepository.findRatingsForSkill(skillId);

        Double averageRating = ratedExchanges.isEmpty() ? 0.0 :
                ratedExchanges.stream()
                        .mapToInt(Exchange::getReceiverRating)
                        .average()
                        .orElse(0.0);

        List<RatingResponse> ratings = ratedExchanges.stream()
                .map(exchange -> {
                    try {
                        UserResponse receiver = getUserById(exchange.getReceiverId(), token);
                        return new RatingResponse(
                                exchange.getId(),
                                exchange.getReceiverRating(),
                                exchange.getReceiverComment(),
                                exchange.getRatingDate(),
                                receiver.firstName() + " " + receiver.lastName(),
                                receiver.id()
                        );
                    } catch (Exception e) {
                        log.error("Error loading receiver details: {}", e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return new SkillRatingStats(
                skillId,
                skill.name(),
                Math.round(averageRating * 10.0) / 10.0,
                ratings.size(),
                ratings
        );
    }

    /**
     * Récupérer tous les échanges complétés non notés d'un receiver
     */
    @Transactional(readOnly = true)
    public List<ExchangeResponse> getUnratedCompletedExchanges(Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        UserResponse currentUser = getUserByKeycloakId(jwt.getSubject(), token);

        List<Exchange> completedExchanges = exchangeRepository.findCompletedExchangesByReceiverId(currentUser.id());

        return completedExchanges.stream()
                .filter(e -> e.getReceiverRating() == null)
                .map(exchange -> {
                    try {
                        SkillResponse skill = getSkillById(exchange.getSkillId());
                        UserResponse receiver = getUserById(exchange.getReceiverId(), token);
                        return new ExchangeResponse(
                                exchange.getId(),
                                exchange.getProducerId(),
                                exchange.getReceiverId(),
                                exchange.getSkillId(),
                                exchange.getStatus(),
                                exchange.getCreatedAt(),
                                exchange.getUpdatedAt(),
                                exchange.getStreamingDate(),
                                exchange.getProducerRating(),
                                exchange.getRejectionReason(),
                                skill != null ? skill.name() : "Compétence supprimée",
                                receiver.firstName() + " " + receiver.lastName(),
                                exchange.getSkillId()
                        );
                    } catch (Exception e) {
                        log.error("Error mapping exchange: {}", e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Vérifier si un receiver a déjà noté un échange
     */
    @Transactional(readOnly = true)
    public boolean hasReceiverRated(Integer exchangeId, Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        UserResponse currentUser = getUserByKeycloakId(jwt.getSubject(), token);

        Exchange exchange = exchangeRepository.findById(exchangeId)
                .orElseThrow(() -> new ExchangeNotFoundException("Exchange not found"));

        // Vérifier que l'utilisateur est le receiver
        if (!exchange.getReceiverId().equals(currentUser.id())) {
            throw new AccessDeniedException("Not authorized");
        }

        return exchange.getReceiverRating() != null;
    }

    // Helper methods
    private UserResponse getUserById(Long userId, String token) {
        try {
            return userServiceClient.getUserById(userId, token);
        } catch (Exception e) {
            log.error("Failed to fetch user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to fetch user", e);
        }
    }

    private UserResponse getUserByKeycloakId(String keycloakId, String token) {
        try {
            return userServiceClient.getUserByKeycloakId(keycloakId, token);
        } catch (Exception e) {
            log.error("Failed to fetch user by Keycloak ID {}: {}", keycloakId, e.getMessage());
            throw new RuntimeException("Failed to fetch user", e);
        }
    }

    private SkillResponse getSkillById(Integer skillId) {
        try {
            return skillServiceClient.getSkillById(skillId);
        } catch (Exception e) {
            log.error("Failed to fetch skill {}: {}", skillId, e.getMessage());
            return null;
        }
    }
}