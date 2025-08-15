package com.example.serviceexchange.service;

import com.example.serviceexchange.FeignClient.SkillServiceClient;
import com.example.serviceexchange.FeignClient.UserServiceClient;
import com.example.serviceexchange.dto.*;
import com.example.serviceexchange.entity.Exchange;
import com.example.serviceexchange.repository.ExchangeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CalendarService {
    private final ExchangeRepository exchangeRepository;
    private final UserServiceClient userServiceClient;
    private final SkillServiceClient skillServiceClient;

    @Transactional(readOnly = true)
    public List<CalendarEventResponse> getCalendarEvents(LocalDate startDate, LocalDate endDate, String view, Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        UserResponse user = getUserByKeycloakId(jwt.getSubject(), token);

        log.info("Fetching calendar events for user {} from {} to {}", user.id(), startDate, endDate);

        List<Exchange> exchanges = exchangeRepository.findUserExchanges(user.id());

        return exchanges.stream()
                .filter(exchange -> isWithinDateRange(exchange, startDate, endDate))
                .map(exchange -> mapToCalendarEvent(exchange, user.id(), token))
                .filter(event -> event != null)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CalendarEventResponse> getProducerEvents(LocalDate startDate, LocalDate endDate, Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        UserResponse producer = getUserByKeycloakId(jwt.getSubject(), token);

        log.info("Fetching producer events for user {}", producer.id());

        List<Exchange> exchanges = exchangeRepository.findByProducerId(producer.id());
        List<CalendarEventResponse> events = new ArrayList<>();

        for (Exchange exchange : exchanges) {
            if (isWithinDateRange(exchange, startDate, endDate)) {
                CalendarEventResponse event = mapToCalendarEvent(exchange, producer.id(), token);
                if (event != null) {
                    event.setRole("PRODUCER");
                    events.add(event);
                }
            }
        }

        return events;
    }

    @Transactional(readOnly = true)
    public List<CalendarEventResponse> getReceiverEvents(LocalDate startDate, LocalDate endDate, Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        UserResponse receiver = getUserByKeycloakId(jwt.getSubject(), token);

        log.info("Fetching receiver events for user {}", receiver.id());

        List<Exchange> exchanges = exchangeRepository.findByReceiverId(receiver.id());
        List<CalendarEventResponse> events = new ArrayList<>();

        for (Exchange exchange : exchanges) {
            if (isWithinDateRange(exchange, startDate, endDate)) {
                CalendarEventResponse event = mapToCalendarEvent(exchange, receiver.id(), token);
                if (event != null) {
                    event.setRole("RECEIVER");
                    events.add(event);
                }
            }
        }

        return events;
    }

    @Transactional(readOnly = true)
    public CalendarEventResponse getEventDetails(Integer exchangeId, Jwt jwt) {
        String token = "Bearer " + jwt.getTokenValue();
        UserResponse user = getUserByKeycloakId(jwt.getSubject(), token);

        Exchange exchange = exchangeRepository.findById(exchangeId)
                .orElseThrow(() -> new RuntimeException("Exchange not found"));

        // Vérifier l'autorisation
        if (!exchange.getProducerId().equals(user.id()) && !exchange.getReceiverId().equals(user.id())) {
            throw new RuntimeException("Unauthorized access to event");
        }

        return mapToCalendarEvent(exchange, user.id(), token);
    }

    @Transactional(readOnly = true)
    public List<CalendarEventResponse> getUpcomingEvents(int days, Jwt jwt) {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(days);

        return getCalendarEvents(startDate, endDate, "upcoming", jwt);
    }

    private CalendarEventResponse mapToCalendarEvent(Exchange exchange, Long userId, String token) {
        try {
            SkillResponse skill = skillServiceClient.getSkillById(exchange.getSkillId());
            UserResponse producer = userServiceClient.getUserById(exchange.getProducerId(), token);
            UserResponse receiver = userServiceClient.getUserById(exchange.getReceiverId(), token);

            String role = exchange.getProducerId().equals(userId) ? "PRODUCER" : "RECEIVER";

            return CalendarEventResponse.builder()
                    .id(exchange.getId())
                    .skillId(exchange.getSkillId())
                    .skillName(skill.name())
                    .skillDescription(skill.description())
                    .producerId(exchange.getProducerId())
                    .producerName(producer.firstName() + " " + producer.lastName())
                    .receiverId(exchange.getReceiverId())
                    .receiverName(receiver.firstName() + " " + receiver.lastName())
                    .status(exchange.getStatus())
                    .streamingDate(exchange.getStreamingDate())
                    .streamingTime(skill.streamingTime())
                    .price(skill.price())
                    .categoryName(skill.categoryName())
                    .role(role)
                    .eventType(determineEventType(exchange.getStatus()))
                    .color(determineEventColor(exchange.getStatus(), role))
                    .createdAt(exchange.getCreatedAt())
                    .updatedAt(exchange.getUpdatedAt())
                    .build();
        } catch (Exception e) {
            log.error("Error mapping exchange {} to calendar event: {}", exchange.getId(), e.getMessage());
            return null;
        }
    }

    private boolean isWithinDateRange(Exchange exchange, LocalDate startDate, LocalDate endDate) {
        if (exchange.getStreamingDate() == null) {
            return false;
        }

        LocalDate exchangeDate = exchange.getStreamingDate().toLocalDate();
        return !exchangeDate.isBefore(startDate) && !exchangeDate.isAfter(endDate);
    }

    private String determineEventType(String status) {
        return switch (status) {
            case "PENDING" -> "pending";
            case "ACCEPTED" -> "accepted";
            case "SCHEDULED" -> "scheduled";
            case "IN_PROGRESS" -> "live";
            case "COMPLETED" -> "completed";
            case "REJECTED" -> "rejected";
            case "CANCELLED" -> "cancelled";
            default -> "unknown";
        };
    }

    private String determineEventColor(String status, String role) {
        if ("PRODUCER".equals(role)) {
            return switch (status) {
                case "PENDING" -> "#fdcb6e";
                case "ACCEPTED", "SCHEDULED" -> "#fd79a8";
                case "IN_PROGRESS" -> "#ff6b6b";
                case "COMPLETED" -> "#00b894";
                case "REJECTED", "CANCELLED" -> "#636e72";
                default -> "#dfe6e9";
            };
        } else {
            return switch (status) {
                case "PENDING" -> "#fdcb6e";
                case "ACCEPTED", "SCHEDULED" -> "#74b9ff";
                case "IN_PROGRESS" -> "#0984e3";
                case "COMPLETED" -> "#00b894";
                case "REJECTED", "CANCELLED" -> "#636e72";
                default -> "#dfe6e9";
            };
        }
    }

    private UserResponse getUserByKeycloakId(String keycloakId, String token) {
        UserResponse user = userServiceClient.getUserByKeycloakId(keycloakId, token);
        if (user == null) {
            throw new RuntimeException("User not found for Keycloak ID: " + keycloakId);
        }
        return user;
    }
}