package com.example.servicemessagerie.config;

import com.example.servicemessagerie.dto.UserResponse;
import com.example.servicemessagerie.feignclient.UserServiceClient;
import com.example.servicemessagerie.controller.PresenceController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.HandshakeInterceptor;
import feign.FeignException;

import java.util.Map;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtDecoder jwtDecoder;
    private final UserServiceClient userServiceClient;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/messaging")
                .setAllowedOriginPatterns("http://localhost:4200")
                .addInterceptors(new MessagingHandshakeInterceptor())
                .withSockJS()
                .setClientLibraryUrl("https://cdn.jsdelivr.net/npm/sockjs-client@1.6.1/dist/sockjs.min.js")
                .setSessionCookieNeeded(false);

        registry.addEndpoint("/ws/messaging")
                .setAllowedOriginPatterns("http://localhost:4200")
                .addInterceptors(new MessagingHandshakeInterceptor());

        log.info("Messagerie WebSocket endpoints registered with SockJS support");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue", "/topic", "/user")
                .setHeartbeatValue(new long[]{25000, 25000})
                .setTaskScheduler(messagingHeartBeatScheduler());

        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");

        log.info("Messagerie broker configured");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new MessagingChannelInterceptor());
        registration.taskExecutor().corePoolSize(10).maxPoolSize(20);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor().corePoolSize(10).maxPoolSize(20);
    }

    @Bean
    public TaskScheduler messagingHeartBeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("messaging-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

    private static class MessagingHandshakeInterceptor implements HandshakeInterceptor {
        @Override
        public boolean beforeHandshake(
                ServerHttpRequest request,
                ServerHttpResponse response,
                WebSocketHandler wsHandler,
                Map<String, Object> attributes
        ) throws Exception {
            log.info("Messagerie WebSocket handshake from: {}", request.getRemoteAddress());
            log.debug("Request URI: {}", request.getURI());

            String query = request.getURI().getQuery();
            if (query != null && query.contains("token=")) {
                String token = query.substring(query.indexOf("token=") + 6);
                if (token.contains("&")) {
                    token = token.substring(0, token.indexOf("&"));
                }
                attributes.put("token", token);
                log.debug("Token extracted from query params");
            }

            return true;
        }

        @Override
        public void afterHandshake(
                ServerHttpRequest request,
                ServerHttpResponse response,
                WebSocketHandler wsHandler,
                Exception exception
        ) {
            if (exception == null) {
                log.info("Messagerie WebSocket handshake completed successfully");
            } else {
                log.error("Messagerie WebSocket handshake failed", exception);
            }
        }
    }

    private class MessagingChannelInterceptor implements ChannelInterceptor {
        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                    message, StompHeaderAccessor.class
            );

            if (accessor != null) {
                log.debug("Messagerie incoming: command={}, destination={}",
                        accessor.getCommand(), accessor.getDestination());

                // FIXED: Handle CONNECT command
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    log.info("Processing messagerie CONNECT command");

                    String token = extractToken(accessor);
                    if (token == null) {
                        log.error("No authorization token found");
                        throw new BadCredentialsException("Missing authorization token");
                    }

                    try {
                        Jwt jwt = jwtDecoder.decode(token);
                        JwtAuthenticationToken authToken = new JwtAuthenticationToken(jwt);
                        accessor.setUser(authToken);

                        Long userId = resolveUserId(jwt, token);

                        log.info("Messagerie WebSocket authentication successful for user: {} (ID: {})",
                                jwt.getSubject(), userId);

                        accessor.getSessionAttributes().put("userId", userId);
                        accessor.getSessionAttributes().put("keycloakId", jwt.getSubject());
                        accessor.getSessionAttributes().put("username", jwt.getClaim("preferred_username"));
                        accessor.getSessionAttributes().put("email", jwt.getClaim("email"));

                    } catch (JwtException e) {
                        log.error("Messagerie JWT validation failed: {}", e.getMessage());
                        throw new BadCredentialsException("Invalid JWT for messaging: " + e.getMessage(), e);
                    } catch (Exception e) {
                        log.error("Messagerie authentication failed: {}", e.getMessage());
                        throw new BadCredentialsException("Authentication failed: " + e.getMessage(), e);
                    }
                }

                // FIXED: Handle DISCONNECT command
                if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
                    log.info("Processing messagerie DISCONNECT command");

                    Long userId = (Long) accessor.getSessionAttributes().get("userId");
                    String sessionId = accessor.getSessionId();

                    if (userId != null) {
                        log.info("User {} disconnecting (session: {})", userId, sessionId);

                        // This will be handled by the SessionDisconnectEvent in PresenceController
                        // But we can also handle it here for immediate response
                        try {
                            // Note: You'll need to inject PresenceController or create a service
                            // For now, we'll let the event listener handle it
                            log.debug("Disconnect will be handled by event listener");
                        } catch (Exception e) {
                            log.error("Error handling disconnect: {}", e.getMessage());
                        }
                    }
                }

                // FIXED: Handle SEND commands with better logging
                if (StompCommand.SEND.equals(accessor.getCommand())) {
                    String destination = accessor.getDestination();
                    if (destination != null) {
                        if (destination.startsWith("/app/conversation/") ||
                                destination.startsWith("/app/typing/")) {
                            log.debug("Message sent to: {}", destination);
                        } else if (destination.startsWith("/app/presence/")) {
                            log.debug("Presence message sent to: {}", destination);
                        }
                    }
                }

                // FIXED: Handle SUBSCRIBE commands
                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    String destination = accessor.getDestination();
                    String sessionId = accessor.getSessionId();
                    Long userId = (Long) accessor.getSessionAttributes().get("userId");

                    log.debug("User {} subscribing to: {} (session: {})", userId, destination, sessionId);
                }

                // FIXED: Handle UNSUBSCRIBE commands
                if (StompCommand.UNSUBSCRIBE.equals(accessor.getCommand())) {
                    String subscriptionId = accessor.getSubscriptionId();
                    String sessionId = accessor.getSessionId();
                    Long userId = (Long) accessor.getSessionAttributes().get("userId");

                    log.debug("User {} unsubscribing from: {} (session: {})", userId, subscriptionId, sessionId);
                }
            }
            return message;
        }

        private String extractToken(StompHeaderAccessor accessor) {
            String token = accessor.getFirstNativeHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                return token.substring(7);
            }

            token = accessor.getFirstNativeHeader("X-Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                return token.substring(7);
            }

            token = accessor.getFirstNativeHeader("token");
            if (token != null) {
                return token;
            }

            Object sessionToken = accessor.getSessionAttributes().get("token");
            if (sessionToken instanceof String) {
                return (String) sessionToken;
            }

            return null;
        }

        private Long resolveUserId(Jwt jwt, String token) {
            try {
                String subject = jwt.getSubject();

                try {
                    return Long.parseLong(subject);
                } catch (NumberFormatException ex) {
                    log.debug("Subject is UUID ({}), fetching user by Keycloak ID", subject);

                    ResponseEntity<UserResponse> userResponseEntity = userServiceClient.getUserByKeycloakId(
                            subject, "Bearer " + token
                    );

                    UserResponse user = userResponseEntity.getBody();

                    if (user != null && user.id() != null) {
                        log.debug("Resolved user ID: {} for Keycloak ID: {}", user.id(), subject);
                        return user.id();
                    } else {
                        log.error("User response has null ID for Keycloak ID: {}", subject);
                        throw new BadCredentialsException("User ID is null");
                    }
                }
            } catch (FeignException.NotFound notFoundEx) {
                log.error("User not found for Keycloak ID: {}", jwt.getSubject());
                throw new BadCredentialsException("User not found");
            } catch (FeignException feignEx) {
                log.error("Feign error calling user service for Keycloak ID {}: {}",
                        jwt.getSubject(), feignEx.getMessage());
                throw new BadCredentialsException("User service unavailable");
            } catch (Exception generalEx) {
                log.error("Failed to fetch user by Keycloak ID {}: {}",
                        jwt.getSubject(), generalEx.getMessage(), generalEx);
                throw new BadCredentialsException("Cannot resolve user ID");
            }
        }
    }
}