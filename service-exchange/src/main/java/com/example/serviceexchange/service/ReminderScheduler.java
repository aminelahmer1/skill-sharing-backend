package com.example.serviceexchange.service;

import com.example.serviceexchange.dto.NotificationEvent;
import com.example.serviceexchange.entity.Exchange;
import com.example.serviceexchange.repository.ExchangeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class ReminderScheduler {

    @Autowired
    private ExchangeRepository exchangeRepository;

    @Autowired
    private KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    @Scheduled(cron = "0 0 * * * *") // Toutes les heures
    public void sendReminders() {
        LocalDateTime now = LocalDateTime.now();
        List<Exchange> upcomingSessions = exchangeRepository.findByStatusAndStreamingDateBetween(
                "ACCEPTED", now, now.plusHours(24)
        );

        for (Exchange session : upcomingSessions) {
            LocalDateTime streamingDate = session.getStreamingDate();
            if (streamingDate.minusHours(24).isBefore(now) && streamingDate.minusHours(23).isAfter(now)) {
                sendReminder(session, "24h");
            } else if (streamingDate.minusHours(1).isBefore(now) && streamingDate.isAfter(now)) {
                sendReminder(session, "1h");
            }
        }
    }

    private void sendReminder(Exchange session, String timeBefore) {
        NotificationEvent event = new NotificationEvent(
                "REMINDER",
                session.getId(),
                session.getProducerId(),
                session.getReceiverId(),
                "Session #" + session.getSkillId(),
                "Rappel : votre session commence dans " + timeBefore,
                session.getStreamingDate().toString()
        );
        kafkaTemplate.send("notifications", event);
    }
}