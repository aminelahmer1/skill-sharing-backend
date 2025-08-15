package com.example.serviceexchange.controller;

import com.example.serviceexchange.dto.CalendarEventResponse;
import com.example.serviceexchange.service.CalendarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/exchanges/calendar")
@RequiredArgsConstructor
@Slf4j
public class CalendarController {
    private final CalendarService calendarService;

    @GetMapping("/events")
    public ResponseEntity<List<CalendarEventResponse>> getCalendarEvents(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String view,
            @AuthenticationPrincipal Jwt jwt
    ) {
        log.info("Fetching calendar events from {} to {} with view: {}", startDate, endDate, view);
        List<CalendarEventResponse> events = calendarService.getCalendarEvents(startDate, endDate, view, jwt);
        return ResponseEntity.ok(events);
    }

    @GetMapping("/events/producer")
    public ResponseEntity<List<CalendarEventResponse>> getProducerCalendarEvents(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal Jwt jwt
    ) {
        log.info("Producer fetching calendar events from {} to {}", startDate, endDate);
        List<CalendarEventResponse> events = calendarService.getProducerEvents(startDate, endDate, jwt);
        return ResponseEntity.ok(events);
    }

    @GetMapping("/events/receiver")
    public ResponseEntity<List<CalendarEventResponse>> getReceiverCalendarEvents(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal Jwt jwt
    ) {
        log.info("Receiver fetching calendar events from {} to {}", startDate, endDate);
        List<CalendarEventResponse> events = calendarService.getReceiverEvents(startDate, endDate, jwt);
        return ResponseEntity.ok(events);
    }

    @GetMapping("/events/{exchangeId}")
    public ResponseEntity<CalendarEventResponse> getEventDetails(
            @PathVariable Integer exchangeId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        CalendarEventResponse event = calendarService.getEventDetails(exchangeId, jwt);
        return ResponseEntity.ok(event);
    }

    @GetMapping("/upcoming")
    public ResponseEntity<List<CalendarEventResponse>> getUpcomingEvents(
            @RequestParam(defaultValue = "7") int days,
            @AuthenticationPrincipal Jwt jwt
    ) {
        List<CalendarEventResponse> events = calendarService.getUpcomingEvents(days, jwt);
        return ResponseEntity.ok(events);
    }
}