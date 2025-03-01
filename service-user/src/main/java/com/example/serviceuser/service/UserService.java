package com.example.serviceuser.service;

import com.example.serviceuser.config.JwtUtil;
import com.example.serviceuser.dto.UserDTO;
import com.example.serviceuser.entity.User;
import com.example.serviceuser.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    // Méthode pour enregistrer un utilisateur
    public String registerUser(UserDTO userDTO) {
        if (userRepository.findByEmail(userDTO.email()) != null) {
            throw new RuntimeException("Email already in use!");
        }

        User user = new User();
        user.setUsername(userDTO.username());
        user.setEmail(userDTO.email());
        user.setPassword(passwordEncoder.encode(userDTO.password()));
        user.setRole(userDTO.role());

        userRepository.save(user);
        return "User registered successfully!";
    }

    // Recherche un utilisateur par username
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(userRepository.findByUsername(username));
    }

    // Recherche un utilisateur par email
    public Optional<User> findByEmail(String email) {
        return Optional.ofNullable(userRepository.findByEmail(email));
    }

    // Connexion d'un utilisateur
    public String loginUser(String email, String password) {
        User user = userRepository.findByEmail(email);

        if (user == null) {
            throw new RuntimeException("User not found!");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Invalid password!");
        }

        // Générer un token JWT
        return jwtUtil.generateToken(user.getEmail(), user.getRole().name());
    }

    // Déconnexion
    public void logout(String token) {
        jwtUtil.invalidateToken(token);
    }
}