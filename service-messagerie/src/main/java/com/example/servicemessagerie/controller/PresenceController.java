package com.example.servicemessagerie.controller;

import com.example.servicemessagerie.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Controller
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;

    // FIXED: Better tracking with session info
    private final Map<Long, UserPresenceInfo> onlineUsers = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionToUserId = new ConcurrentHashMap<>();

    private static class UserPresenceInfo {
        public final Set<String> sessionIds = ConcurrentHashMap.newKeySet();
        public long lastActivity = System.currentTimeMillis();
        public boolean isOnline = true;

        public void addSession(String sessionId) {
            sessionIds.add(sessionId);
            lastActivity = System.currentTimeMillis();
            isOnline = true;
        }

        public void removeSession(String sessionId) {
            sessionIds.remove(sessionId);
            lastActivity = System.currentTimeMillis();
            isOnline = !sessionIds.isEmpty();
        }

        public void updateActivity() {
            lastActivity = System.currentTimeMillis();
        }
    }

    /**
     * FIXED: Handle WebSocket connection events
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        try {
            SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(event.getMessage());
            String sessionId = headers.getSessionId();

            // Get user ID from session attributes
            Long userId = (Long) headers.getSessionAttributes().get("userId");

            if (userId != null && sessionId != null) {
                log.info("🔌 User {} connected with session {}", userId, sessionId);

                // Track session to user mapping
                sessionToUserId.put(sessionId, userId);

                // Update presence info
                onlineUsers.computeIfAbsent(userId, k -> new UserPresenceInfo())
                        .addSession(sessionId);

                // Broadcast presence update
                broadcastPresenceUpdate(userId, true);
                broadcastOnlineUsersList();

                log.info("✅ User {} is now ONLINE (session: {})", userId, sessionId);
            }
        } catch (Exception e) {
            log.error("❌ Error handling connection event: {}", e.getMessage());
        }
    }

    /**
     * FIXED: Handle WebSocket disconnection events
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        try {
            SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(event.getMessage());
            String sessionId = headers.getSessionId();

            if (sessionId != null) {
                Long userId = sessionToUserId.remove(sessionId);

                if (userId != null) {
                    log.info("🔌 User {} disconnected (session: {})", userId, sessionId);

                    UserPresenceInfo presenceInfo = onlineUsers.get(userId);
                    if (presenceInfo != null) {
                        presenceInfo.removeSession(sessionId);

                        // If no more sessions, user is offline
                        if (presenceInfo.sessionIds.isEmpty()) {
                            onlineUsers.remove(userId);
                            broadcastPresenceUpdate(userId, false);
                            log.info("❌ User {} is now OFFLINE (no active sessions)", userId);
                        } else {
                            log.info("ℹ️ User {} still has {} active session(s)",
                                    userId, presenceInfo.sessionIds.size());
                        }

                        broadcastOnlineUsersList();
                    }
                }
            }
        } catch (Exception e) {
            log.error("❌ Error handling disconnection event: {}", e.getMessage());
        }
    }

    /**
     * FIXED: Endpoint pour obtenir la liste des utilisateurs en ligne
     */
    @MessageMapping("/presence/request-online-users")
    @SendTo("/topic/online-users")
    public List<Long> requestOnlineUsers() {
        // Clean up inactive users first
        cleanupInactiveUsers();

        List<Long> onlineUserIds = new ArrayList<>(onlineUsers.keySet());
        log.info("📡 Requested online users list: {}", onlineUserIds);
        return onlineUserIds;
    }

    /**
     * FIXED: Endpoint pour mettre à jour le statut de présence avec validation
     */
    @MessageMapping("/presence/update")
    public void updatePresence(@Payload Map<String, Object> presenceData,
                               SimpMessageHeaderAccessor headerAccessor) {
        try {
            // Get user ID from JWT token in session
            JwtAuthenticationToken auth = (JwtAuthenticationToken) headerAccessor.getUser();
            if (auth == null) {
                log.error("❌ No authentication found for presence update");
                return;
            }

            Long userId = (Long) headerAccessor.getSessionAttributes().get("userId");
            if (userId == null) {
                log.error("❌ No user ID found in session for presence update");
                return;
            }

            String sessionId = headerAccessor.getSessionId();
            Boolean isOnline = Boolean.valueOf(presenceData.get("isOnline").toString());

            log.debug("📡 Processing presence update: user={}, online={}, session={}",
                    userId, isOnline, sessionId);

            if (isOnline) {
                // Update presence info
                UserPresenceInfo presenceInfo = onlineUsers.computeIfAbsent(userId, k -> new UserPresenceInfo());
                if (sessionId != null) {
                    presenceInfo.addSession(sessionId);
                    sessionToUserId.put(sessionId, userId);
                }
                presenceInfo.updateActivity();

                log.info("✅ User {} presence updated to ONLINE", userId);
            } else {
                // Handle explicit offline status
                UserPresenceInfo presenceInfo = onlineUsers.get(userId);
                if (presenceInfo != null && sessionId != null) {
                    presenceInfo.removeSession(sessionId);
                    if (presenceInfo.sessionIds.isEmpty()) {
                        onlineUsers.remove(userId);
                    }
                }
                sessionToUserId.remove(sessionId);

                log.info("❌ User {} presence updated to OFFLINE", userId);
            }

            // Broadcast updates
            broadcastPresenceUpdate(userId, isOnline);
            broadcastOnlineUsersList();

        } catch (Exception e) {
            log.error("❌ Error updating presence: {}", e.getMessage());
        }
    }

    /**
     * FIXED: Endpoint pour le statut personnel de l'utilisateur
     */
    @MessageMapping("/user/presence")
    public void getUserPresence(@Payload Map<String, Object> request,
                                SimpMessageHeaderAccessor headerAccessor) {
        try {
            Long userId = (Long) headerAccessor.getSessionAttributes().get("userId");
            if (userId == null) {
                log.error("❌ No user ID found for personal presence request");
                return;
            }

            log.info("👤 Personal presence request for user: {}", userId);

            // Clean up before sending current list
            cleanupInactiveUsers();

            Map<String, Object> response = Map.of(
                    "onlineUsers", new ArrayList<>(onlineUsers.keySet()),
                    "currentUserId", userId,
                    "timestamp", System.currentTimeMillis(),
                    "totalOnline", onlineUsers.size()
            );

            // Send to specific user
            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/presence-update",
                    response
            );

        } catch (Exception e) {
            log.error("❌ Error handling personal presence request: {}", e.getMessage());
        }
    }

    /**
     * FIXED: Nettoyage périodique des utilisateurs inactifs avec meilleure logique
     */
    public void cleanupInactiveUsers() {
        long currentTime = System.currentTimeMillis();
        long timeout = 2 * 60 * 1000; // REDUCED: 2 minutes instead of 5

        List<Long> usersToRemove = new ArrayList<>();

        onlineUsers.entrySet().forEach(entry -> {
            Long userId = entry.getKey();
            UserPresenceInfo presenceInfo = entry.getValue();

            // Check if user has been inactive for too long
            boolean isInactive = (currentTime - presenceInfo.lastActivity) > timeout;

            if (isInactive) {
                log.info("🧹 Marking inactive user as offline: {} (last activity: {} seconds ago)",
                        userId, (currentTime - presenceInfo.lastActivity) / 1000);
                usersToRemove.add(userId);
            }
        });

        // Remove inactive users and broadcast updates
        for (Long userId : usersToRemove) {
            onlineUsers.remove(userId);
            // Remove all sessions for this user
            sessionToUserId.entrySet().removeIf(entry -> entry.getValue().equals(userId));

            broadcastPresenceUpdate(userId, false);
        }

        if (!usersToRemove.isEmpty()) {
            broadcastOnlineUsersList();
            log.info("🧹 Cleaned up {} inactive users", usersToRemove.size());
        }
    }

    /**
     * FIXED: Broadcast presence update with better error handling
     */
    private void broadcastPresenceUpdate(Long userId, boolean isOnline) {
        try {
            Map<String, Object> presenceUpdate = Map.of(
                    "userId", userId,
                    "isOnline", isOnline,
                    "status", isOnline ? "ONLINE" : "OFFLINE",
                    "timestamp", System.currentTimeMillis()
            );

            // Broadcast to all subscribers
            messagingTemplate.convertAndSend("/topic/presence", presenceUpdate);

            log.debug("📡 Broadcasted presence update: user {} is {}",
                    userId, isOnline ? "ONLINE" : "OFFLINE");

        } catch (Exception e) {
            log.error("❌ Error broadcasting presence update: {}", e.getMessage());
        }
    }

    /**
     * FIXED: Broadcast online users list with validation
     */
    private void broadcastOnlineUsersList() {
        try {
            // Clean up before broadcasting
            cleanupInactiveUsers();

            List<Long> onlineUserIds = new ArrayList<>(onlineUsers.keySet());

            // Validate user IDs
            onlineUserIds.removeIf(id -> id == null || id <= 0);

            log.debug("📡 Broadcasting online users list: {} users", onlineUserIds.size());

            messagingTemplate.convertAndSend("/topic/online-users", onlineUserIds);

        } catch (Exception e) {
            log.error("❌ Error broadcasting online users list: {}", e.getMessage());
        }
    }

    /**
     * FIXED: Get current online users with cleanup
     */
    public List<Long> getCurrentOnlineUsers() {
        cleanupInactiveUsers();
        return new ArrayList<>(onlineUsers.keySet());
    }

    /**
     * FIXED: Check if specific user is online
     */
    public boolean isUserOnline(Long userId) {
        if (userId == null || userId <= 0) {
            return false;
        }

        UserPresenceInfo presenceInfo = onlineUsers.get(userId);
        if (presenceInfo == null) {
            return false;
        }

        // Check if user has recent activity
        long currentTime = System.currentTimeMillis();
        long timeout = 2 * 60 * 1000; // 2 minutes

        if ((currentTime - presenceInfo.lastActivity) > timeout) {
            // User is inactive, remove them
            onlineUsers.remove(userId);
            broadcastPresenceUpdate(userId, false);
            broadcastOnlineUsersList();
            return false;
        }

        return presenceInfo.isOnline;
    }

    /**
     * FIXED: Manual user offline (for explicit logouts)
     */
    public void setUserOffline(Long userId, String sessionId) {
        if (userId == null) return;

        log.info("🚪 Manually setting user {} offline (session: {})", userId, sessionId);

        UserPresenceInfo presenceInfo = onlineUsers.get(userId);
        if (presenceInfo != null) {
            if (sessionId != null) {
                presenceInfo.removeSession(sessionId);
                sessionToUserId.remove(sessionId);
            }

            // If no more sessions, remove user completely
            if (presenceInfo.sessionIds.isEmpty()) {
                onlineUsers.remove(userId);
                broadcastPresenceUpdate(userId, false);
                broadcastOnlineUsersList();
                log.info("❌ User {} is now OFFLINE (manual)", userId);
            }
        }
    }

    /**
     * FIXED: Force user online (for explicit connections)
     */
    public void setUserOnline(Long userId, String sessionId) {
        if (userId == null) return;

        log.info("🔌 Manually setting user {} online (session: {})", userId, sessionId);

        UserPresenceInfo presenceInfo = onlineUsers.computeIfAbsent(userId, k -> new UserPresenceInfo());
        if (sessionId != null) {
            presenceInfo.addSession(sessionId);
            sessionToUserId.put(sessionId, userId);
        }
        presenceInfo.updateActivity();

        broadcastPresenceUpdate(userId, true);
        broadcastOnlineUsersList();

        log.info("✅ User {} is now ONLINE (manual)", userId);
    }

    /**
     * FIXED: Debug endpoint to check current state
     */
    @MessageMapping("/presence/debug")
    public void debugPresence(@Payload Map<String, Object> request,
                              SimpMessageHeaderAccessor headerAccessor) {
        try {
            Long requestingUserId = (Long) headerAccessor.getSessionAttributes().get("userId");

            Map<String, Object> debugInfo = new HashMap<>();
            debugInfo.put("timestamp", System.currentTimeMillis());
            debugInfo.put("totalOnlineUsers", onlineUsers.size());
            debugInfo.put("onlineUserIds", new ArrayList<>(onlineUsers.keySet()));
            debugInfo.put("totalSessions", sessionToUserId.size());
            debugInfo.put("requestingUserId", requestingUserId);

            // Add detailed user info
            Map<Long, Map<String, Object>> userDetails = new HashMap<>();
            onlineUsers.forEach((userId, info) -> {
                Map<String, Object> details = new HashMap<>();
                details.put("sessionCount", info.sessionIds.size());
                details.put("lastActivity", info.lastActivity);
                details.put("isOnline", info.isOnline);
                details.put("secondsSinceLastActivity", (System.currentTimeMillis() - info.lastActivity) / 1000);
                userDetails.put(userId, details);
            });
            debugInfo.put("userDetails", userDetails);

            log.info("🐛 Debug info requested by user {}: {}", requestingUserId, debugInfo);

            // Send debug info back to requesting user
            if (requestingUserId != null) {
                messagingTemplate.convertAndSendToUser(
                        requestingUserId.toString(),
                        "/queue/presence-debug",
                        debugInfo
                );
            }

        } catch (Exception e) {
            log.error("❌ Error in debug presence: {}", e.getMessage());
        }
    }

    /**
     * FIXED: Scheduled cleanup with better logging
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60000) // Every minute
    public void scheduledCleanup() {
        try {
            int sizeBefore = onlineUsers.size();
            cleanupInactiveUsers();
            int sizeAfter = onlineUsers.size();

            if (sizeBefore != sizeAfter) {
                log.info("🧹 Scheduled cleanup: {} users removed, {} users remain online",
                        sizeBefore - sizeAfter, sizeAfter);
            }
        } catch (Exception e) {
            log.error("❌ Error in scheduled cleanup: {}", e.getMessage());
        }
    }

    /**
     * FIXED: Health check endpoint
     */
    @MessageMapping("/presence/ping")
    public void pingPresence(@Payload Map<String, Object> request,
                             SimpMessageHeaderAccessor headerAccessor) {
        try {
            Long userId = (Long) headerAccessor.getSessionAttributes().get("userId");
            String sessionId = headerAccessor.getSessionId();

            if (userId != null) {
                // Update activity timestamp
                UserPresenceInfo presenceInfo = onlineUsers.get(userId);
                if (presenceInfo != null) {
                    presenceInfo.updateActivity();
                    log.debug("💓 Ping received from user {} (session: {})", userId, sessionId);
                } else {
                    // User not in online list, add them
                    log.info("🔄 Ping from unknown user {}, adding to online list", userId);
                    setUserOnline(userId, sessionId);
                }

                // Send pong response
                messagingTemplate.convertAndSendToUser(
                        userId.toString(),
                        "/queue/presence-pong",
                        Map.of("timestamp", System.currentTimeMillis(), "status", "ok")
                );
            }
        } catch (Exception e) {
            log.error("❌ Error handling ping: {}", e.getMessage());
        }
    }
}