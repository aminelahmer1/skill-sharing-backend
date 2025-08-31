package com.example.servicemessagerie.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class PresenceService {

    private final Map<Long, Long> onlineUsers = new ConcurrentHashMap<>();

    public void markUserOnline(Long userId) {
        onlineUsers.put(userId, System.currentTimeMillis());
        log.info("✅ User {} marked as online", userId);
    }

    public void markUserOffline(Long userId) {
        onlineUsers.remove(userId);
        log.info("❌ User {} marked as offline", userId);
    }

    public boolean isUserOnline(Long userId) {
        return onlineUsers.containsKey(userId);
    }

    public List<Long> getOnlineUsers() {
        return new ArrayList<>(onlineUsers.keySet());
    }

    /**
     * Nettoyage automatique toutes les 5 minutes
     */
    @Scheduled(fixedDelay = 300000)
    public void cleanupInactiveUsers() {
        long currentTime = System.currentTimeMillis();
        long timeout = 5 * 60 * 1000;

        int removedCount = 0;
        Iterator<Map.Entry<Long, Long>> iterator = onlineUsers.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Long, Long> entry = iterator.next();
            if ((currentTime - entry.getValue()) > timeout) {
                iterator.remove();
                removedCount++;
                log.info("🧹 Removed inactive user: {}", entry.getKey());
            }
        }

        if (removedCount > 0) {
            log.info("🧹 Cleaned up {} inactive users", removedCount);
        }
    }
}