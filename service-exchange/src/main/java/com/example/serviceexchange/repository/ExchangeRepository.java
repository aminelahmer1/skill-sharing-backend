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
}