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

    // ==============================================
// CORRECTIONS POUR RatingService.java
// ==============================================

    /**
     * Récupérer les statistiques complètes du dashboard producteur
     */
    @Transactional(readOnly = true)
    public ProducerDashboardStats getProducerDashboardStats(Long producerId, Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();

        // Calculate date ranges for current and last month
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime monthStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime monthEnd = monthStart.plusMonths(1);
        LocalDateTime lastMonthStart = monthStart.minusMonths(1);
        LocalDateTime lastMonthEnd = monthStart;

        // Calculate start date for charts (last 12 months)
        LocalDateTime chartStartDate = now.minusMonths(12).withDayOfMonth(1);

        // Métriques de base
        int upcomingSessions = exchangeRepository.countUpcomingSessions(producerId);
        List<Integer> skillIds = exchangeRepository.findSkillIdsByProducerId(producerId);
        Double avgRating = exchangeRepository.calculateAverageRatingForProducer(producerId);
        int totalStudents = exchangeRepository.countUniqueStudents(producerId);

        // Performance
        Double completionRate = exchangeRepository.calculateCompletionRate(producerId);
        Double rebookingRate = exchangeRepository.calculateRebookingRate(producerId);
        Double satisfactionRate = exchangeRepository.calculateSatisfactionRate(producerId);
        Double responseTime = exchangeRepository.calculateAverageResponseTime(producerId);

        // Croissance avec paramètres de dates corrects
        int sessionsThisMonth = exchangeRepository.countSessionsThisMonth(producerId, monthStart, monthEnd);
        int sessionsLastMonth = exchangeRepository.countSessionsLastMonth(producerId, lastMonthStart, lastMonthEnd);
        double monthlyGrowthRate = calculateGrowthRate(sessionsThisMonth, sessionsLastMonth);
        int newStudents = exchangeRepository.countNewStudentsThisMonth(producerId, monthStart, monthEnd);
        int teachingHours = exchangeRepository.calculateTotalTeachingHours(producerId);

        // Comparaison plateforme - CORRECTION: utiliser la méthode correcte
        List<Object[]> allProducersRanking = exchangeRepository.getAllProducersRanking();
        int ranking = calculateProducerRanking(producerId, allProducersRanking);
        Double platformAvg = exchangeRepository.getPlatformAverageRating();

        // Données pour graphiques avec paramètres corrects
        List<MonthlyActivityData> monthlyActivity = buildMonthlyActivityData(producerId, chartStartDate);
        List<SkillPerformanceData> skillPerformance = buildSkillPerformanceData(producerId, token);
        List<RatingEvolutionData> ratingEvolution = buildRatingEvolutionData(producerId, chartStartDate);

        return new ProducerDashboardStats(
                upcomingSessions,
                skillIds.size(),
                roundToOneDecimal(avgRating),
                totalStudents,
                roundToOneDecimal(completionRate),
                roundToOneDecimal(rebookingRate),
                roundToOneDecimal(satisfactionRate),
                roundToOneDecimal(responseTime),
                sessionsThisMonth,
                sessionsLastMonth,
                monthlyGrowthRate,
                newStudents,
                teachingHours,
                ranking,
                roundToOneDecimal(platformAvg),
                monthlyActivity,
                skillPerformance,
                ratingEvolution
        );
    }

    /**
     * Récupérer les statistiques d'engagement détaillées
     */
    @Transactional(readOnly = true)
    public ProducerEngagementStats getProducerEngagementStats(Long producerId, Jwt jwt) {
        Double completionRate = exchangeRepository.calculateCompletionRate(producerId);
        Double avgDuration = exchangeRepository.calculateAverageSessionDuration(producerId);
        Double rebookingRate = exchangeRepository.calculateRebookingRate(producerId);
        int uniqueStudents = exchangeRepository.countUniqueStudents(producerId);

        // Calcul des interactions totales
        List<Exchange> allExchanges = exchangeRepository.findAllValidExchangesByProducerId(producerId);
        int totalInteractions = allExchanges.size();

        // Taux de rétention approximatif
        double retentionRate = rebookingRate != null ? rebookingRate : 0.0;

        return new ProducerEngagementStats(
                roundToOneDecimal(completionRate),
                roundToOneDecimal(avgDuration),
                roundToOneDecimal(rebookingRate),
                uniqueStudents,
                totalInteractions,
                retentionRate
        );
    }

    /**
     * Récupérer les statistiques de croissance
     */
    @Transactional(readOnly = true)
    public ProducerGrowthStats getProducerGrowthStats(Long producerId, Jwt jwt) {
        // Calculate date ranges
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime monthStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime monthEnd = monthStart.plusMonths(1);
        LocalDateTime lastMonthStart = monthStart.minusMonths(1);
        LocalDateTime lastMonthEnd = monthStart;

        int sessionsThisMonth = exchangeRepository.countSessionsThisMonth(producerId, monthStart, monthEnd);
        int sessionsLastMonth = exchangeRepository.countSessionsLastMonth(producerId, lastMonthStart, lastMonthEnd);
        double monthlyGrowth = calculateGrowthRate(sessionsThisMonth, sessionsLastMonth);
        int newStudents = exchangeRepository.countNewStudentsThisMonth(producerId, monthStart, monthEnd);
        int teachingHours = exchangeRepository.calculateTotalTeachingHours(producerId);

        // Croissance année sur année (approximation)
        double yearGrowth = monthlyGrowth * 12; // Simplification

        return new ProducerGrowthStats(
                sessionsThisMonth,
                sessionsLastMonth,
                monthlyGrowth,
                newStudents,
                teachingHours,
                yearGrowth
        );
    }

    /**
     * Récupérer les statistiques de qualité
     */
    @Transactional(readOnly = true)
    public ProducerQualityStats getProducerQualityStats(Long producerId, Jwt jwt) {
        Double responseTime = exchangeRepository.calculateAverageResponseTime(producerId);
        Double satisfactionRate = exchangeRepository.calculateSatisfactionRate(producerId);
        Integer totalRatings = exchangeRepository.countRatingsForProducer(producerId);
        Double avgRating = exchangeRepository.calculateAverageRatingForProducer(producerId);

        // Distribution des ratings
        List<Exchange> ratedExchanges = exchangeRepository.findCompletedExchangesWithRatings(producerId);
        Map<Integer, Long> distribution = ratedExchanges.stream()
                .collect(Collectors.groupingBy(Exchange::getReceiverRating, Collectors.counting()));

        List<RatingDistribution> ratingDistribution = new ArrayList<>();
        for (int stars = 1; stars <= 5; stars++) {
            long count = distribution.getOrDefault(stars, 0L);
            double percentage = totalRatings > 0 ? (count * 100.0) / totalRatings : 0.0;
            ratingDistribution.add(new RatingDistribution(stars, (int) count, percentage));
        }

        // Tendance qualité (comparaison 3 derniers mois vs 3 mois précédents)
        LocalDateTime chartStartDate = LocalDateTime.now().minusMonths(12).withDayOfMonth(1);
        String qualityTrend = calculateQualityTrend(producerId, chartStartDate);

        return new ProducerQualityStats(
                roundToOneDecimal(responseTime),
                roundToOneDecimal(satisfactionRate),
                totalRatings,
                roundToOneDecimal(avgRating),
                ratingDistribution,
                qualityTrend
        );
    }

// ==============================================
// MÉTHODES HELPER PRIVÉES CORRIGÉES
// ==============================================

    private List<MonthlyActivityData> buildMonthlyActivityData(Long producerId, LocalDateTime startDate) {
        List<Object[]> rawData = exchangeRepository.getMonthlyActivityData(producerId, startDate);
        return rawData.stream()
                .map(row -> new MonthlyActivityData(
                        ((Number) row[0]).intValue(), // year
                        ((Number) row[1]).intValue(), // month
                        getMonthLabel(((Number) row[1]).intValue()),
                        ((Number) row[2]).intValue(), // completed
                        ((Number) row[3]).intValue()  // upcoming
                ))
                .collect(Collectors.toList());
    }

    private List<SkillPerformanceData> buildSkillPerformanceData(Long producerId, String token) {
        List<Object[]> performanceData = exchangeRepository.getSkillPerformanceStats(producerId);
        List<Object[]> pendingData = exchangeRepository.getPendingRequestsBySkill(producerId);

        Map<Integer, Integer> pendingMap = pendingData.stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).intValue(),
                        row -> ((Number) row[1]).intValue()
                ));

        return performanceData.stream()
                .map(row -> {
                    int skillId = ((Number) row[0]).intValue();
                    double avgRating = ((Number) row[1]).doubleValue();
                    int sessions = ((Number) row[2]).intValue();
                    int pending = pendingMap.getOrDefault(skillId, 0);

                    String skillName = getSkillName(skillId);
                    boolean isTopPerforming = avgRating >= 4.5 && sessions >= 5;

                    return new SkillPerformanceData(
                            skillId, skillName, avgRating, sessions, pending, isTopPerforming
                    );
                })
                .collect(Collectors.toList());
    }

    private List<RatingEvolutionData> buildRatingEvolutionData(Long producerId, LocalDateTime startDate) {
        List<Object[]> rawData = exchangeRepository.getRatingEvolutionData(producerId, startDate);
        return rawData.stream()
                .map(row -> new RatingEvolutionData(
                        ((Number) row[0]).intValue(),
                        ((Number) row[1]).intValue(),
                        getMonthLabel(((Number) row[1]).intValue()),
                        ((Number) row[2]).doubleValue()
                ))
                .collect(Collectors.toList());
    }

    private double calculateGrowthRate(int current, int previous) {
        if (previous == 0) return current > 0 ? 100.0 : 0.0;
        return ((double) (current - previous) / previous) * 100.0;
    }

    private int calculateProducerRanking(Long producerId, List<Object[]> allProducersRanking) {
        for (int i = 0; i < allProducersRanking.size(); i++) {
            Long currentProducerId = ((Number) allProducersRanking.get(i)[0]).longValue();
            if (currentProducerId.equals(producerId)) {
                return i + 1; // Position dans le ranking (1-based)
            }
        }
        return 0; // Non classé
    }

    private String calculateQualityTrend(Long producerId, LocalDateTime startDate) {
        // Logique pour déterminer la tendance
        // Comparaison des 3 derniers mois vs 3 mois précédents
        List<Object[]> recentData = exchangeRepository.getRatingEvolutionData(producerId, startDate);

        if (recentData.size() < 3) return "STABLE";

        double recentAvg = recentData.stream()
                .limit(3)
                .mapToDouble(row -> ((Number) row[2]).doubleValue())
                .average()
                .orElse(0.0);

        double previousAvg = recentData.stream()
                .skip(3)
                .limit(3)
                .mapToDouble(row -> ((Number) row[2]).doubleValue())
                .average()
                .orElse(recentAvg);

        if (recentAvg > previousAvg + 0.2) return "IMPROVING";
        if (recentAvg < previousAvg - 0.2) return "DECLINING";
        return "STABLE";
    }

    private String getMonthLabel(int month) {
        String[] months = {"Jan", "Fév", "Mar", "Avr", "Mai", "Jun",
                "Jul", "Aoû", "Sep", "Oct", "Nov", "Déc"};
        return months[month - 1];
    }
    /**
     * Récupérer ses propres statistiques (producteur connecté)
     */
    @Transactional(readOnly = true)
    public ProducerRatingStats getMyRatingStats(Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();

        // Obtenir l'utilisateur connecté
        UserResponse currentUser = getUserByKeycloakId(jwt.getSubject(), token);

        log.info("Getting rating stats for connected producer: {}", currentUser.id());

        // Utiliser la méthode existante avec l'ID du producteur connecté
        return getProducerRatingStats(currentUser.id(), jwt);
    }
    private String getSkillName(int skillId) {
        try {
            SkillResponse skill = skillServiceClient.getSkillById(skillId);
            return skill != null ? skill.name() : "Compétence #" + skillId;
        } catch (Exception e) {
            return "Compétence #" + skillId;
        }
    }

    private double roundToOneDecimal(Double value) {
        return value != null ? Math.round(value * 10.0) / 10.0 : 0.0;
    }








}