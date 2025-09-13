package com.example.serviceexchange.repository;

import com.example.serviceexchange.entity.Exchange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExchangeRepository extends JpaRepository<Exchange, Integer> {
    List<Exchange> findByProducerId(Long producerId);
    List<Exchange> findByReceiverId(Long receiverId);
    List<Exchange> findBySkillId(Integer skillId);
    List<Exchange> findBySkillIdAndStatus(Integer skillId, String status);
    List<Exchange> findByProducerIdOrReceiverId(Long producerId, Long receiverId);

    @Query("SELECT e FROM Exchange e WHERE e.producerId = :userId OR e.receiverId = :userId ORDER BY e.streamingDate DESC")
    List<Exchange> findUserExchanges(@Param("userId") Long userId);

    @Query("SELECT COUNT(e) > 0 FROM Exchange e WHERE e.producerId = :producerId AND e.receiverId = :receiverId AND e.skillId = :skillId")
    boolean existsByProducerIdAndReceiverIdAndSkillId(
            @Param("producerId") Long producerId,
            @Param("receiverId") Long receiverId,
            @Param("skillId") Integer skillId);

    List<Exchange> findByProducerIdAndStatus(Long producerId, String status);
    List<Exchange> findByReceiverIdAndStatusIn(Long receiverId, List<String> statuses);
    @Query("SELECT e FROM Exchange e WHERE e.status = :status AND e.streamingDate BETWEEN :start AND :end")
    List<Exchange> findByStatusAndStreamingDateBetween(
            @Param("status") String status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
    List<Exchange> findByReceiverIdAndStatus(Long receiverId, String status);
    @Query("SELECT e FROM Exchange e WHERE e.skillId = :skillId AND (e.producerId = :userId OR e.receiverId = :userId)")
    List<Exchange> findBySkillIdAndUserId(@Param("skillId") Integer skillId, @Param("userId") Long userId);

    // Add method for receiver check in getExchangesBySkillId
    @Query("SELECT e FROM Exchange e WHERE e.skillId = :skillId AND e.receiverId = :receiverId")
    Optional<Exchange> findBySkillIdAndReceiverId(@Param("skillId") Integer skillId, @Param("receiverId") Long receiverId);


        // Récupérer tous les échanges complétés d'un producteur avec ratings
        @Query("SELECT e FROM Exchange e WHERE e.producerId = :producerId AND e.status = 'COMPLETED' AND e.receiverRating IS NOT NULL")
        List<Exchange> findCompletedExchangesWithRatings(@Param("producerId") Long producerId);

        // Récupérer tous les échanges complétés d'un producteur
        @Query("SELECT e FROM Exchange e WHERE e.producerId = :producerId AND e.status = 'COMPLETED'")
        List<Exchange> findCompletedExchangesByProducerId(@Param("producerId") Long producerId);

        // Calculer la note moyenne d'un producteur
        @Query("SELECT AVG(e.receiverRating) FROM Exchange e WHERE e.producerId = :producerId AND e.receiverRating IS NOT NULL")
        Double calculateAverageRatingForProducer(@Param("producerId") Long producerId);

        // Compter le nombre total de ratings pour un producteur
        @Query("SELECT COUNT(e) FROM Exchange e WHERE e.producerId = :producerId AND e.receiverRating IS NOT NULL")
        Integer countRatingsForProducer(@Param("producerId") Long producerId);

        // Récupérer les ratings par étoiles pour un producteur
        @Query("SELECT e.receiverRating as stars, COUNT(e) as count FROM Exchange e WHERE e.producerId = :producerId AND e.receiverRating IS NOT NULL GROUP BY e.receiverRating")
        List<Object[]> getRatingDistributionForProducer(@Param("producerId") Long producerId);

        // Récupérer les derniers ratings d'un producteur
        @Query("SELECT e FROM Exchange e WHERE e.producerId = :producerId AND e.receiverRating IS NOT NULL ORDER BY e.ratingDate DESC")
        List<Exchange> findRecentRatingsForProducer(@Param("producerId") Long producerId);

        // Récupérer les échanges complétés d'un receiver pour rating
        @Query("SELECT e FROM Exchange e WHERE e.receiverId = :receiverId AND e.status = 'COMPLETED'")
        List<Exchange> findCompletedExchangesByReceiverId(@Param("receiverId") Long receiverId);

        // Vérifier si un receiver a déjà noté un échange
        @Query("SELECT CASE WHEN e.receiverRating IS NOT NULL THEN true ELSE false END FROM Exchange e WHERE e.id = :exchangeId")
        boolean hasReceiverRated(@Param("exchangeId") Integer exchangeId);

        // Récupérer les ratings pour une compétence spécifique
        @Query("SELECT e FROM Exchange e WHERE e.skillId = :skillId AND e.status = 'COMPLETED' AND e.receiverRating IS NOT NULL")
        List<Exchange> findRatingsForSkill(@Param("skillId") Integer skillId);

        // Calculer la note moyenne pour une compétence
        @Query("SELECT AVG(e.receiverRating) FROM Exchange e WHERE e.skillId = :skillId AND e.receiverRating IS NOT NULL")
        Double calculateAverageRatingForSkill(@Param("skillId") Integer skillId);
    /**
     * Récupère les échanges par skillId et liste de statuts
     */
    List<Exchange> findBySkillIdAndStatusIn(Integer skillId, List<String> statuses);

    /**
     * Récupère tous les receivers pour un skill donné (tous statuts sauf PENDING et REJECTED)
     */
    @Query("SELECT DISTINCT e FROM Exchange e WHERE e.skillId = :skillId AND e.status NOT IN ('PENDING', 'REJECTED', 'CANCELLED')")
    List<Exchange> findAcceptedReceiversBySkillId(@Param("skillId") Integer skillId);

    @Query("SELECT DISTINCT e FROM Exchange e WHERE e.producerId = :producerId AND e.status NOT IN ('PENDING', 'REJECTED', 'CANCELLED')")
    List<Exchange> findAllSubscribersExchangesByProducerId(@Param("producerId") Long producerId);
    /**
     * Récupère tous les skill IDs auxquels un receiver est inscrit avec des statuts valides
     */
    @Query("SELECT DISTINCT e.skillId FROM Exchange e WHERE e.receiverId = :receiverId AND e.status NOT IN ('PENDING', 'REJECTED', 'CANCELLED')")
    List<Integer> findSkillIdsByReceiverId(@Param("receiverId") Long receiverId);

    /**
     * Récupère tous les receivers (sauf le receiver courant) inscrits aux mêmes compétences
     * avec des statuts valides
     */
    @Query("""
    SELECT DISTINCT e FROM Exchange e 
    WHERE e.skillId IN :skillIds 
    AND e.receiverId != :currentReceiverId 
    AND e.status NOT IN ('PENDING', 'REJECTED', 'CANCELLED')
    """)
    List<Exchange> findPeerReceiversBySkillIds(@Param("skillIds") List<Integer> skillIds,
                                               @Param("currentReceiverId") Long currentReceiverId);
    /**
     * Version optimisée : récupère directement les IDs des peer receivers
     */
    @Query("""
    SELECT DISTINCT e.receiverId 
    FROM Exchange e 
    WHERE e.skillId IN :skillIds 
    AND e.receiverId != :currentReceiverId 
    AND e.status IN ('ACCEPTED', 'COMPLETED', 'IN_PROGRESS', 'SCHEDULED')
    """)
    List<Long> findPeerReceiverIdsBySkillIds(@Param("skillIds") List<Integer> skillIds,
                                             @Param("currentReceiverId") Long currentReceiverId);
    /**
     * Récupère tous les autres receivers pour une compétence spécifique (excluant le receiver courant)
     */
    @Query("""
    SELECT DISTINCT e FROM Exchange e 
    WHERE e.skillId = :skillId 
    AND e.receiverId != :currentReceiverId 
    AND e.status NOT IN ('PENDING', 'REJECTED', 'CANCELLED')
    """)
    List<Exchange> findOtherReceiversForSkill(@Param("skillId") Integer skillId,
                                              @Param("currentReceiverId") Long currentReceiverId);

    /**
     * Récupère tous les membres (producers et receivers) pour les compétences d'un receiver
     */
    @Query("""
    SELECT DISTINCT e FROM Exchange e 
    WHERE e.skillId IN :skillIds 
    AND e.status NOT IN ('PENDING', 'REJECTED', 'CANCELLED')
    """)
    List<Exchange> findAllMembersForSkills(@Param("skillIds") List<Integer> skillIds);
    /**
     * Récupère tous les échanges du receiver avec les détails nécessaires pour la communauté
     */
    @Query("""
    SELECT e FROM Exchange e 
    WHERE e.receiverId = :receiverId 
    AND e.status NOT IN ('PENDING', 'REJECTED', 'CANCELLED')
    ORDER BY e.skillId, e.createdAt DESC
    """)
    List<Exchange> findReceiverExchangesForCommunity(@Param("receiverId") Long receiverId);

    /**
     * Récupère tous les échanges pour une compétence avec statuts valides
     */
    @Query("""
    SELECT e FROM Exchange e 
    WHERE e.skillId = :skillId 
    AND e.status NOT IN ('PENDING', 'REJECTED', 'CANCELLED')
    ORDER BY e.createdAt DESC
    """)
    List<Exchange> findValidExchangesBySkillId(@Param("skillId") Integer skillId);

    /**
     * Récupère les échanges d'une compétence excluant un utilisateur spécifique
     */
    @Query("""
    SELECT e FROM Exchange e 
    WHERE e.skillId = :skillId 
    AND e.receiverId != :excludeUserId 
    AND e.status NOT IN ('PENDING', 'REJECTED', 'CANCELLED')
    ORDER BY e.createdAt DESC
    """)
    List<Exchange> findValidExchangesBySkillIdExcludingUser(@Param("skillId") Integer skillId,
                                                            @Param("excludeUserId") Long excludeUserId);

    /**
     * Vérifie si un utilisateur est inscrit à une compétence
     */
    @Query("""
    SELECT COUNT(e) > 0 FROM Exchange e 
    WHERE e.skillId = :skillId 
    AND e.receiverId = :userId 
    AND e.status NOT IN ('PENDING', 'REJECTED', 'CANCELLED')
    """)
    boolean isUserEnrolledInSkill(@Param("skillId") Integer skillId, @Param("userId") Long userId);

    /**
     * Récupère les statistiques de statuts pour une compétence
     */
    @Query("""
    SELECT e.status, COUNT(e) 
    FROM Exchange e 
    WHERE e.skillId = :skillId 
    AND e.status NOT IN ('PENDING', 'REJECTED', 'CANCELLED')
    GROUP BY e.status
    """)
    List<Object[]> getStatusStatsForSkill(@Param("skillId") Integer skillId);
// Ajouter ces méthodes dans ExchangeRepository.java

    /**
     * Récupère tous les skill IDs d'un producteur avec leurs exchanges valides
     */
    @Query("""
    SELECT DISTINCT e.skillId 
    FROM Exchange e 
    WHERE e.producerId = :producerId 
    AND e.status NOT IN ('PENDING', 'REJECTED', 'CANCELLED')
    """)
    List<Integer> findSkillIdsByProducerId(@Param("producerId") Long producerId);

    /**
     * Récupère tous les receivers pour un producteur (toutes ses compétences)
     */
    @Query("""
    SELECT DISTINCT e FROM Exchange e 
    WHERE e.producerId = :producerId 
    AND e.status NOT IN ('PENDING', 'REJECTED', 'CANCELLED')
    ORDER BY e.skillId, e.createdAt DESC
    """)
    List<Exchange> findAllValidExchangesByProducerId(@Param("producerId") Long producerId);

    /**
     * Récupère tous les exchanges d'un receiver avec détails pour toutes ses compétences
     */
    @Query("""
    SELECT e FROM Exchange e 
    WHERE e.receiverId = :receiverId 
    AND e.status NOT IN ('PENDING', 'REJECTED', 'CANCELLED')
    ORDER BY e.skillId, e.createdAt DESC
    """)
    List<Exchange> findAllValidExchangesByReceiverId(@Param("receiverId") Long receiverId);

    /**
     * Récupère tous les autres receivers pour une liste de compétences (excluant le receiver courant)
     */
    @Query("""
    SELECT e FROM Exchange e 
    WHERE e.skillId IN :skillIds 
    AND e.receiverId != :excludeReceiverId 
    AND e.status NOT IN ('PENDING', 'REJECTED', 'CANCELLED')
    ORDER BY e.skillId, e.createdAt DESC
    """)
    List<Exchange> findOtherReceiversForSkills(@Param("skillIds") List<Integer> skillIds,
                                               @Param("excludeReceiverId") Long excludeReceiverId);

    /**
     * Récupère tous les exchanges pour une liste de compétences avec statuts valides
     */
    @Query("""
    SELECT e FROM Exchange e 
    WHERE e.skillId IN :skillIds 
    AND e.status NOT IN ('PENDING', 'REJECTED', 'CANCELLED')
    ORDER BY e.skillId, e.createdAt DESC
    """)
    List<Exchange> findValidExchangesBySkillIds(@Param("skillIds") List<Integer> skillIds);

    /**
     * Récupère tous les skills d'un producteur avec leurs receivers
     */
    @Query("""
    SELECT DISTINCT e.skillId 
    FROM Exchange e 
    WHERE e.producerId = :producerId 
    AND e.status NOT IN ('PENDING', 'REJECTED', 'CANCELLED')
    ORDER BY e.skillId
    """)
    List<Integer> findSkillIdsWithReceivers(@Param("producerId") Long producerId);

    /**
     * Récupère tous les receivers pour un skill spécifique d'un producteur
     */
    @Query("""
    SELECT e FROM Exchange e 
    WHERE e.producerId = :producerId 
    AND e.skillId = :skillId 
    AND e.status NOT IN ('PENDING', 'REJECTED', 'CANCELLED')
    ORDER BY e.createdAt DESC
    """)
    List<Exchange> findReceiversBySkillId(@Param("producerId") Long producerId, @Param("skillId") Integer skillId);

    /**
     * Récupère toutes les compétences du producteur avec leurs échanges groupés
     */
    @Query("""
    SELECT e FROM Exchange e 
    WHERE e.producerId = :producerId 
    AND e.status NOT IN ('PENDING', 'REJECTED', 'CANCELLED')
    AND e.streamingDate BETWEEN :startDate AND :endDate
    ORDER BY e.skillId, e.streamingDate, e.createdAt DESC
    """)
    List<Exchange> findProducerSkillsWithReceivers(
            @Param("producerId") Long producerId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);



// Requêtes ExchangeRepository.java - Corrections finales

    /**
     * STATISTIQUES D'ENGAGEMENT PRODUCTEUR
     */

// 1. Sessions à venir pour le producteur
    @Query("SELECT COUNT(e) FROM Exchange e WHERE e.producerId = :producerId AND e.status IN ('SCHEDULED', 'ACCEPTED') AND e.streamingDate > CURRENT_TIMESTAMP")
    Integer countUpcomingSessions(@Param("producerId") Long producerId);

    // 2. Taux de complétion des sessions
    @Query("SELECT CASE WHEN COUNT(e) = 0 THEN 0.0 ELSE " +
            "CAST(SUM(CASE WHEN e.status = 'COMPLETED' THEN 1 ELSE 0 END) AS DOUBLE) / " +
            "CAST(COUNT(e) AS DOUBLE) * 100.0 END " +
            "FROM Exchange e " +
            "WHERE e.producerId = :producerId " +
            "AND e.status NOT IN ('PENDING', 'REJECTED')")
    Double calculateCompletionRate(@Param("producerId") Long producerId);

    // 3. Temps moyen des sessions (version simple en heures)
    @Query("SELECT COALESCE(AVG(2.0), 0.0) " +
            "FROM Exchange e " +
            "WHERE e.producerId = :producerId AND e.status = 'COMPLETED'")
    Double calculateAverageSessionDuration(@Param("producerId") Long producerId);

    // 4. Taux de re-booking (version simplifiée)
    @Query("SELECT CASE WHEN (SELECT COUNT(DISTINCT e2.receiverId) FROM Exchange e2 WHERE e2.producerId = :producerId) = 0 THEN 0.0 ELSE " +
            "CAST((SELECT COUNT(DISTINCT e1.receiverId) FROM Exchange e1 " +
            "WHERE e1.producerId = :producerId " +
            "AND e1.receiverId IN (" +
            "  SELECT e3.receiverId FROM Exchange e3 " +
            "  WHERE e3.producerId = :producerId " +
            "  GROUP BY e3.receiverId " +
            "  HAVING COUNT(e3.id) > 1" +
            ")) AS DOUBLE) / " +
            "CAST((SELECT COUNT(DISTINCT e2.receiverId) FROM Exchange e2 WHERE e2.producerId = :producerId) AS DOUBLE) * 100.0 END")
    Double calculateRebookingRate(@Param("producerId") Long producerId);

    /**
     * MÉTRIQUES DE CROISSANCE
     */

// 5. Apprenants uniques touchés
    @Query("SELECT COUNT(DISTINCT e.receiverId) FROM Exchange e WHERE e.producerId = :producerId AND e.status NOT IN ('PENDING', 'REJECTED', 'CANCELLED')")
    Integer countUniqueStudents(@Param("producerId") Long producerId);

    // 6. Sessions ce mois
    @Query("SELECT COUNT(e) FROM Exchange e " +
            "WHERE e.producerId = :producerId " +
            "AND e.status = 'COMPLETED' " +
            "AND e.streamingDate >= :monthStart " +
            "AND e.streamingDate < :monthEnd")
    Integer countSessionsThisMonth(@Param("producerId") Long producerId,
                                   @Param("monthStart") LocalDateTime monthStart,
                                   @Param("monthEnd") LocalDateTime monthEnd);

    // 7. Sessions mois précédent
    @Query("SELECT COUNT(e) FROM Exchange e " +
            "WHERE e.producerId = :producerId " +
            "AND e.status = 'COMPLETED' " +
            "AND e.streamingDate >= :lastMonthStart " +
            "AND e.streamingDate < :lastMonthEnd")
    Integer countSessionsLastMonth(@Param("producerId") Long producerId,
                                   @Param("lastMonthStart") LocalDateTime lastMonthStart,
                                   @Param("lastMonthEnd") LocalDateTime lastMonthEnd);

    // 8. Nouvelles inscriptions ce mois
    @Query("SELECT COUNT(DISTINCT e.receiverId) FROM Exchange e " +
            "WHERE e.producerId = :producerId " +
            "AND e.status NOT IN ('PENDING', 'REJECTED', 'CANCELLED') " +
            "AND e.createdAt >= :monthStart " +
            "AND e.createdAt < :monthEnd")
    Integer countNewStudentsThisMonth(@Param("producerId") Long producerId,
                                      @Param("monthStart") LocalDateTime monthStart,
                                      @Param("monthEnd") LocalDateTime monthEnd);

    // 9. Heures d'enseignement totales
    @Query("SELECT COALESCE(COUNT(e) * 2, 0) FROM Exchange e WHERE e.producerId = :producerId AND e.status = 'COMPLETED'")
    Integer calculateTotalTeachingHours(@Param("producerId") Long producerId);

    /**
     * QUALITÉ DU SERVICE
     */

// 10. Temps de réponse moyen (en heures approximatif)
    @Query("SELECT COALESCE(AVG(24.0), 0.0) " +
            "FROM Exchange e " +
            "WHERE e.producerId = :producerId " +
            "AND e.status IN ('ACCEPTED', 'REJECTED')")
    Double calculateAverageResponseTime(@Param("producerId") Long producerId);

    // 11. Taux de satisfaction (notes >= 4)
    @Query("SELECT CASE WHEN COUNT(e) = 0 THEN 0.0 ELSE " +
            "CAST(SUM(CASE WHEN e.receiverRating >= 4 THEN 1 ELSE 0 END) AS DOUBLE) / " +
            "CAST(COUNT(e) AS DOUBLE) * 100.0 END " +
            "FROM Exchange e " +
            "WHERE e.producerId = :producerId AND e.receiverRating IS NOT NULL")
    Double calculateSatisfactionRate(@Param("producerId") Long producerId);

    /**
     * STATISTIQUES PAR COMPÉTENCE
     */

// 12. Performance par skill
    @Query("SELECT e.skillId, " +
            "CASE WHEN COUNT(e) = 0 THEN 0.0 ELSE AVG(CAST(e.receiverRating AS DOUBLE)) END, " +
            "COUNT(e) " +
            "FROM Exchange e " +
            "WHERE e.producerId = :producerId " +
            "AND e.receiverRating IS NOT NULL " +
            "GROUP BY e.skillId " +
            "ORDER BY AVG(CAST(e.receiverRating AS DOUBLE)) DESC")
    List<Object[]> getSkillPerformanceStats(@Param("producerId") Long producerId);

    // 13. Compétence la plus demandée
    @Query("SELECT e.skillId, COUNT(e) " +
            "FROM Exchange e " +
            "WHERE e.producerId = :producerId " +
            "AND e.status NOT IN ('PENDING', 'REJECTED') " +
            "GROUP BY e.skillId " +
            "ORDER BY COUNT(e) DESC")
    List<Object[]> getMostRequestedSkills(@Param("producerId") Long producerId);

    // 14. Demandes en attente par compétence
    @Query("SELECT e.skillId, COUNT(e) " +
            "FROM Exchange e " +
            "WHERE e.producerId = :producerId " +
            "AND e.status = 'PENDING' " +
            "GROUP BY e.skillId")
    List<Object[]> getPendingRequestsBySkill(@Param("producerId") Long producerId);

    /**
     * ÉVOLUTION TEMPORELLE
     */

// 15. Données pour graphique mensuel
    @Query("SELECT " +
            "EXTRACT(YEAR FROM e.streamingDate), " +
            "EXTRACT(MONTH FROM e.streamingDate), " +
            "SUM(CASE WHEN e.status = 'COMPLETED' THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN e.status IN ('SCHEDULED', 'ACCEPTED') THEN 1 ELSE 0 END) " +
            "FROM Exchange e " +
            "WHERE e.producerId = :producerId " +
            "AND e.streamingDate >= :startDate " +
            "GROUP BY EXTRACT(YEAR FROM e.streamingDate), EXTRACT(MONTH FROM e.streamingDate) " +
            "ORDER BY EXTRACT(YEAR FROM e.streamingDate), EXTRACT(MONTH FROM e.streamingDate)")
    List<Object[]> getMonthlyActivityData(@Param("producerId") Long producerId, @Param("startDate") LocalDateTime startDate);

    // 16. Évolution des notes par mois
    @Query("SELECT " +
            "EXTRACT(YEAR FROM e.ratingDate), " +
            "EXTRACT(MONTH FROM e.ratingDate), " +
            "CASE WHEN COUNT(e) = 0 THEN 0.0 ELSE AVG(CAST(e.receiverRating AS DOUBLE)) END " +
            "FROM Exchange e " +
            "WHERE e.producerId = :producerId " +
            "AND e.receiverRating IS NOT NULL " +
            "AND e.ratingDate >= :startDate " +
            "GROUP BY EXTRACT(YEAR FROM e.ratingDate), EXTRACT(MONTH FROM e.ratingDate) " +
            "ORDER BY EXTRACT(YEAR FROM e.ratingDate), EXTRACT(MONTH FROM e.ratingDate)")
    List<Object[]> getRatingEvolutionData(@Param("producerId") Long producerId, @Param("startDate") LocalDateTime startDate);

    /**
     * COMPARAISON SECTEUR
     */

// 17. Données du producteur pour ranking
    @Query("SELECT " +
            "CASE WHEN COUNT(e) = 0 THEN 0.0 ELSE AVG(CAST(e.receiverRating AS DOUBLE)) END, " +
            "COUNT(e) " +
            "FROM Exchange e " +
            "WHERE e.receiverRating IS NOT NULL " +
            "AND e.producerId = :producerId")
    List<Object[]> getProducerRankingData(@Param("producerId") Long producerId);

    // 18. Tous les producteurs pour calcul du ranking
    @Query("SELECT " +
            "e.producerId, " +
            "CASE WHEN COUNT(e) = 0 THEN 0.0 ELSE AVG(CAST(e.receiverRating AS DOUBLE)) END, " +
            "COUNT(e) " +
            "FROM Exchange e " +
            "WHERE e.receiverRating IS NOT NULL " +
            "GROUP BY e.producerId " +
            "HAVING COUNT(e) >= 5 " +
            "ORDER BY AVG(CAST(e.receiverRating AS DOUBLE)) DESC, COUNT(e) DESC")
    List<Object[]> getAllProducersRanking();

    // 19. Moyenne générale de la plateforme
    @Query("SELECT CASE WHEN COUNT(e) = 0 THEN 0.0 ELSE AVG(CAST(e.receiverRating AS DOUBLE)) END FROM Exchange e WHERE e.receiverRating IS NOT NULL")
    Double getPlatformAverageRating();






}