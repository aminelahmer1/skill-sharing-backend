package com.example.servicemessagerie.repository;

import com.example.servicemessagerie.entity.LivestreamSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LivestreamSessionRepository extends JpaRepository<LivestreamSession, Long> {
}