package com.example.serviceuser.repository;


import com.example.serviceuser.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find a user by their Keycloak ID.
     *
     * @param keycloakId The Keycloak ID.
     * @return Optional containing the user if found.
     */
    Optional<User> findByKeycloakId(String keycloakId);

    // Vérifier l'existence par ID Keycloak
    boolean existsByKeycloakId(String keycloakId);

    // Trouver un utilisateur par email
    Optional<User> findByEmail(String email);

    // Méthodes existantes à conserver
    Optional<User> findByUsername(String username);
    boolean existsById(Long id);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END " +
            "FROM User u WHERE u.username = :username OR u.email = :email")
    boolean existsByUsernameOrEmail(@Param("username") String username,
                                    @Param("email") String email);
}