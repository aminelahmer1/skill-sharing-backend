package com.example.serviceuser.repository;

import com.example.serviceuser.entity.InvalidToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


public interface InvalidTokenRepository extends JpaRepository<InvalidToken, String> {
    boolean existsByToken(String token);
}