package com.example.serviceexchange.service;

import com.example.serviceexchange.FeignClient.SkillServiceClient;
import com.example.serviceexchange.dto.NotificationEvent;
import com.example.serviceexchange.dto.SkillResponse;
import com.example.serviceexchange.entity.Exchange;
import com.example.serviceexchange.repository.ExchangeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// SOLUTION 1: Injecter directement SkillServiceClient dans ReminderSchedulerService

@Service
@RequiredArgsConstructor
@Slf4j
public class ReminderSchedulerService {
    private final ExchangeRepository exchangeRepository;
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;
    private final SkillServiceClient skillServiceClient; // DIRECT - Pas via ExchangeService

    // Cache pour éviter d'envoyer plusieurs fois le même rappel
    private final Set<String> sentReminders24h = ConcurrentHashMap.newKeySet();
    private final Set<String> sentReminders1h = ConcurrentHashMap.newKeySet();

    // Exécution toutes les 5 minutes
    @Scheduled(fixedDelay = 300000, initialDelay = 60000)
    @Transactional(readOnly = true)
    public void checkAndSendReminders() {
        log.info("Checking for upcoming livestream sessions to send reminders...");
        LocalDateTime now = LocalDateTime.now();

        try {
            List<Exchange> upcomingExchanges = exchangeRepository.findByStatusAndStreamingDateBetween(
                    "ACCEPTED",
                    now,
                    now.plusHours(25)
            );

            log.info("Found {} upcoming exchanges", upcomingExchanges.size());

            for (Exchange exchange : upcomingExchanges) {
                processExchangeReminders(exchange, now);
            }

            cleanupReminderCache(now);

        } catch (Exception e) {
            log.error("Error in reminder scheduler: ", e);
        }
    }

    private void processExchangeReminders(Exchange exchange, LocalDateTime now) {
        if (exchange.getStreamingDate() == null) {
            return;
        }

        LocalDateTime streamingDate = exchange.getStreamingDate();
        long hoursUntilStream = ChronoUnit.HOURS.between(now, streamingDate);
        long minutesUntilStream = ChronoUnit.MINUTES.between(now, streamingDate);

        String reminderKey24h = exchange.getId() + "_24h";
        String reminderKey1h = exchange.getId() + "_1h";

        // Rappel 24h avant
        if (hoursUntilStream <= 24 && hoursUntilStream > 23 && !sentReminders24h.contains(reminderKey24h)) {
            sendReminder(exchange, "24_HOUR_REMINDER");
            sentReminders24h.add(reminderKey24h);
            log.info("Sent 24h reminder for exchange ID: {}", exchange.getId());
        }

        // Rappel 1h avant
        if (minutesUntilStream <= 60 && minutesUntilStream > 55 && !sentReminders1h.contains(reminderKey1h)) {
            sendReminder(exchange, "1_HOUR_REMINDER");
            sentReminders1h.add(reminderKey1h);
            log.info("Sent 1h reminder for exchange ID: {}", exchange.getId());
        }
    }

    private void sendReminder(Exchange exchange, String reminderType) {
        try {
            // CORRECTION: Récupération directe via SkillServiceClient
            String skillName = getSkillName(exchange.getSkillId());

            NotificationEvent event = new NotificationEvent(
                    reminderType,
                    exchange.getId(),
                    exchange.getProducerId(),
                    exchange.getReceiverId(),
                    skillName,
                    null,
                    exchange.getStreamingDate().toString()
            );

            kafkaTemplate.send("notifications", event);
            log.info("Reminder notification sent to Kafka for exchange ID: {} (type: {}, skill: {})",
                    exchange.getId(), reminderType, skillName);

        } catch (Exception e) {
            log.error("Failed to send reminder for exchange ID: {}", exchange.getId(), e);
        }
    }

    // CORRECTION: Utilisation directe de SkillServiceClient
    private String getSkillName(Integer skillId) {
        try {
            SkillResponse skill = skillServiceClient.getSkillById(skillId);
            if (skill != null && skill.name() != null) {
                return skill.name();
            } else {
                log.warn("Skill name not found for ID: {}, using fallback", skillId);
                return "Compétence #" + skillId;
            }
        } catch (Exception e) {
            log.error("Error fetching skill name for ID: {}, using fallback", skillId, e);
            return "Compétence #" + skillId;
        }
    }

    private void cleanupReminderCache(LocalDateTime now) {
        try {
            List<Exchange> pastExchanges = exchangeRepository.findByStatusAndStreamingDateBetween(
                    "COMPLETED",
                    now.minusDays(2),
                    now
            );

            for (Exchange exchange : pastExchanges) {
                String reminderKey24h = exchange.getId() + "_24h";
                String reminderKey1h = exchange.getId() + "_1h";
                sentReminders24h.remove(reminderKey24h);
                sentReminders1h.remove(reminderKey1h);
            }

            log.debug("Cleaned up reminder cache, removed {} entries", pastExchanges.size());

        } catch (Exception e) {
            log.error("Error cleaning reminder cache: ", e);
        }
    }

    public void checkImmediateReminders(Exchange exchange) {
        if (exchange.getStreamingDate() == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime streamingDate = exchange.getStreamingDate();
        long hoursUntilStream = ChronoUnit.HOURS.between(now, streamingDate);

        if (hoursUntilStream < 24 && hoursUntilStream > 1) {
            sendReminder(exchange, "24_HOUR_REMINDER");
            sentReminders24h.add(exchange.getId() + "_24h");
        }

        if (hoursUntilStream <= 1 && hoursUntilStream >= 0) {
            sendReminder(exchange, "1_HOUR_REMINDER");
            sentReminders1h.add(exchange.getId() + "_1h");
        }
    }
}