package com.example.serviceskill.repository;

import com.example.serviceskill.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SkillRepository extends JpaRepository<Skill, Integer> {

    @Query("SELECT s FROM Skill s WHERE " +
            "(LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(s.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +

            "(:categoryId IS NULL OR s.category.id = :categoryId) AND " +
            "(:minPrice IS NULL OR s.price >= :minPrice) AND " +
            "(:maxPrice IS NULL OR s.price <= :maxPrice)")
    List<Skill> findByKeywordAndFilters(
            @Param("keyword") String keyword,

            @Param("categoryId") Integer categoryId,
            @Param("minPrice") Double minPrice,
            @Param("maxPrice") Double maxPrice
    );
}