package com.example.serviceuser.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**") // Autoriser tous les endpoints
                        .allowedOrigins("http://localhost:4200") // Autoriser le frontend Angular
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // Méthodes autorisées
                        .allowedHeaders("Authorization", "Content-Type") // Autoriser tous les en-têtes
                        .allowCredentials(true); // Autoriser les cookies et les en-têtes d'authentification
            }
        };
    }
}