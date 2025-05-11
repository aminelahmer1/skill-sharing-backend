package com.example.serviceuser.configuration;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.web.cors.CorsConfiguration;
//import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
//import org.springframework.web.filter.CorsFilter;
//import org.springframework.web.servlet.config.annotation.CorsRegistry;
//import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
//
//import java.util.List;
//
//@Configuration
//public class CorsConfig implements WebMvcConfigurer {
//    @Override
//    public void addCorsMappings(CorsRegistry registry) {
//        registry.addMapping("/api/v1/**")
//                .allowedOrigins("http://localhost:4200")
//                .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
//                .allowedHeaders("*")
//                .allowCredentials(true);
//    }
//}