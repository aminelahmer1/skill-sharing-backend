package com.example.serviceexchange.service;

import org.springframework.stereotype.Service;

@Service
public class EmailService {
    public void sendEmail(String to, String subject, String content) {
        // Implémentation réelle utiliserait JavaMailSender ou un service d'email tiers
        System.out.printf("Sending email to %s: %s - %s%n", to, subject, content);
    }
}