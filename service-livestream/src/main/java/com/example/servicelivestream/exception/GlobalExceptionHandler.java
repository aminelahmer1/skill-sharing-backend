package com.example.servicelivestream.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException e) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", e.getReason());
        response.put("status", e.getStatusCode().value());
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.status(e.getStatusCode()).body(response);
    }

    @ExceptionHandler(LiveKitOperationException.class)
    public ResponseEntity<Map<String, Object>> handleLiveKitException(LiveKitOperationException e) {
        log.error("LiveKit operation failed", e);

        Map<String, Object> response = new HashMap<>();
        response.put("error", "LiveKit operation failed");
        response.put("message", e.getMessage());
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception e) {
        log.error("Unexpected error", e);

        Map<String, Object> response = new HashMap<>();
        response.put("error", "Internal server error");
        response.put("message", "An unexpected error occurred");
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    // Gestion des exceptions WebSocket
    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public Map<String, Object> handleWebSocketException(Exception e) {
        log.error("WebSocket error", e);

        Map<String, Object> response = new HashMap<>();
        response.put("error", "WebSocket error");
        response.put("message", e.getMessage());
        response.put("timestamp", LocalDateTime.now());

        return response;
    }
}