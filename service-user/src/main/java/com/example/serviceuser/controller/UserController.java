package com.example.serviceuser.controller;

import com.example.serviceuser.entity.User;
import com.example.serviceuser.entity.Role;
import com.example.serviceuser.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/profile")
    public Map<String, Object> getProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName(); // Récupère l'email de l'utilisateur connecté
        User user = userRepository.findByEmail(email); // Récupère l'utilisateur par email

        Map<String, Object> profile = new HashMap<>();
        profile.put("username", user.getUsername());
        profile.put("email", user.getEmail());
        profile.put("role", user.getRole()); // Ajoutez le rôle de l'utilisateur

        // Ajoutez des informations spécifiques en fonction du rôle
        if (user.getRole() == Role.ROLE_PROVIDER) {
            profile.put("skills", user.getProvidedSkills()); // Exemple : compétences fournies
        } else if (user.getRole() == Role.ROLE_RECEIVER) {
            profile.put("skillsNeeded", user.getNeededSkills()); // Exemple : compétences recherchées
        }

        return profile;
    }
}