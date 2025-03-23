package com.example.serviceexchange.repository;

import com.example.serviceexchange.entity.Exchange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExchangeRepository extends JpaRepository<Exchange, Integer> {
    List<Exchange> findByProviderId(Long providerId);
    List<Exchange> findByReceiverId(Long receiverId);
    List<Exchange> findBySkillId(Integer skillId);
}