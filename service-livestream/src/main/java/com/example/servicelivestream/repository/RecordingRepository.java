package com.example.servicelivestream.repository;

import com.example.servicelivestream.entity.Recording;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecordingRepository extends JpaRepository<Recording, Long> {
    List<Recording> findByAuthorizedUsersContaining(Long userId);
}