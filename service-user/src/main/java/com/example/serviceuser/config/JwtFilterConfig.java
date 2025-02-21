package com.example.serviceuser.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtFilterConfig {

    private final JwtUtil jwtUtil;

    public JwtFilterConfig(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Bean
    public com.example.serviceuser.config.JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new com.example.serviceuser.config.JwtAuthenticationFilter(jwtUtil);
    }
}
