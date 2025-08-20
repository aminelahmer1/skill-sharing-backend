package com.example.servicemessagerie.config;

import com.example.servicemessagerie.dto.UserResponse;
import com.example.servicemessagerie.feignclient.UserServiceClient;
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
        // ✅ Endpoint principal SockJS pour messagerie
        registry.addEndpoint("/ws/messaging")
                .setAllowedOriginPatterns("http://localhost:4200")
                .addInterceptors(new MessagingHandshakeInterceptor()).addInterceptors(new MessagingHandshakeInterceptor())
                .withSockJS()
                .setClientLibraryUrl("https://cdn.jsdelivr.net/npm/sockjs-client@1.6.1/dist/sockjs.min.js")
                .setSessionCookieNeeded(false); // Important pour éviter les problèmes de cookies

        // ✅ Endpoint WebSocket natif (fallback)
        registry.addEndpoint("/ws/messaging")
                .setAllowedOriginPatterns("http://localhost:4200")
                .addInterceptors(new MessagingHandshakeInterceptor());

        log.info("✅ Messagerie WebSocket endpoints registered with SockJS support");
    }
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // ✅ Configuration broker pour messagerie
        registry.enableSimpleBroker("/queue", "/topic", "/user")
                .setHeartbeatValue(new long[]{25000, 25000}) // Heartbeat optimisé
                .setTaskScheduler(messagingHeartBeatScheduler());

        // ✅ Prefixes d'application
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");

        log.info("✅ Messagerie broker configured");
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

    // ✅ Intercepteur de handshake
    private static class MessagingHandshakeInterceptor implements HandshakeInterceptor {
        @Override
        public boolean beforeHandshake(
                ServerHttpRequest request,
                ServerHttpResponse response,
                WebSocketHandler wsHandler,
                Map<String, Object> attributes
        ) throws Exception {
            log.info("🤝 Messagerie WebSocket handshake from: {}", request.getRemoteAddress());
            log.debug("🔍 Request URI: {}", request.getURI());

            // ✅ Extraire le token depuis les paramètres de requête (fallback)
            String query = request.getURI().getQuery();
            if (query != null && query.contains("token=")) {
                String token = query.substring(query.indexOf("token=") + 6);
                if (token.contains("&")) {
                    token = token.substring(0, token.indexOf("&"));
                }
                attributes.put("token", token);
                log.debug("🎫 Token extracted from query params");
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
                log.info("✅ Messagerie WebSocket handshake completed successfully");
            } else {
                log.error("❌ Messagerie WebSocket handshake failed", exception);
            }
        }
    }

    // ✅ Intercepteur de canal (authentification)
    private class MessagingChannelInterceptor implements ChannelInterceptor {
        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                    message, StompHeaderAccessor.class
            );

            if (accessor != null) {
                log.debug("📨 Messagerie incoming: command={}, destination={}",
                        accessor.getCommand(), accessor.getDestination());

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    log.info("🔐 Processing messagerie CONNECT command");

                    // ✅ Authentification JWT
                    String token = extractToken(accessor);
                    if (token == null) {
                        log.error("❌ No authorization token found");
                        throw new BadCredentialsException("Missing authorization token");
                    }

                    try {
                        // ✅ Validation et décodage JWT
                        Jwt jwt = jwtDecoder.decode(token);
                        JwtAuthenticationToken authToken = new JwtAuthenticationToken(jwt);
                        accessor.setUser(authToken);

                        // ✅ Résolution de l'ID utilisateur (UUID → Long)
                        Long userId = resolveUserId(jwt, token);

                        log.info("✅ Messagerie WebSocket authentication successful for user: {} (ID: {})",
                                jwt.getSubject(), userId);

                        // ✅ Stockage des attributs de session
                        accessor.getSessionAttributes().put("userId", userId);
                        accessor.getSessionAttributes().put("keycloakId", jwt.getSubject());
                        accessor.getSessionAttributes().put("username", jwt.getClaim("preferred_username"));
                        accessor.getSessionAttributes().put("email", jwt.getClaim("email"));

                    } catch (JwtException e) {
                        log.error("❌ Messagerie JWT validation failed: {}", e.getMessage());
                        throw new BadCredentialsException("Invalid JWT for messaging: " + e.getMessage(), e);
                    } catch (Exception e) {
                        log.error("❌ Messagerie authentication failed: {}", e.getMessage());
                        throw new BadCredentialsException("Authentication failed: " + e.getMessage(), e);
                    }
                }

                // ✅ Log des messages envoyés
                if (StompCommand.SEND.equals(accessor.getCommand())) {
                    String destination = accessor.getDestination();
                    if (destination != null && (destination.startsWith("/app/conversation/") ||
                            destination.startsWith("/app/typing/"))) {
                        log.debug("💬 Message envoyé vers: {}", destination);
                    }
                }
            }
            return message;
        }

        // ✅ Extraction du token JWT depuis différentes sources
        private String extractToken(StompHeaderAccessor accessor) {
            // 1. Header Authorization standard
            String token = accessor.getFirstNativeHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                return token.substring(7);
            }

            // 2. Header X-Authorization (fallback)
            token = accessor.getFirstNativeHeader("X-Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                return token.substring(7);
            }

            // 3. Token direct
            token = accessor.getFirstNativeHeader("token");
            if (token != null) {
                return token;
            }

            // 4. Depuis les attributs de session (handshake)
            Object sessionToken = accessor.getSessionAttributes().get("token");
            if (sessionToken instanceof String) {
                return (String) sessionToken;
            }

            return null;
        }

        // ✅ : Résolution de l'ID utilisateur avec gestion ResponseEntity
        private Long resolveUserId(Jwt jwt, String token) {
            try {
                String subject = jwt.getSubject();

                // 1. Essayer de parser directement comme Long
                try {
                    return Long.parseLong(subject);
                } catch (NumberFormatException ex) {
                    // 2. Si c'est un UUID Keycloak, appeler le service utilisateur
                    log.debug("Subject is UUID ({}), fetching user by Keycloak ID", subject);

                    ResponseEntity<UserResponse> userResponseEntity = userServiceClient.getUserByKeycloakId(
                            subject, "Bearer " + token
                    );

                    UserResponse user = userResponseEntity.getBody();

                    if (user != null && user.id() != null) {
                        log.debug("✅ Resolved user ID: {} for Keycloak ID: {}", user.id(), subject);
                        return user.id();
                    } else {
                        log.error("❌ User response has null ID for Keycloak ID: {}", subject);
                        throw new BadCredentialsException("User ID is null");
                    }
                }
            } catch (FeignException.NotFound notFoundEx) {
                log.error("❌ User not found for Keycloak ID: {}", jwt.getSubject());
                throw new BadCredentialsException("User not found");
            } catch (FeignException feignEx) {
                log.error("❌ Feign error calling user service for Keycloak ID {}: {}",
                        jwt.getSubject(), feignEx.getMessage());
                throw new BadCredentialsException("User service unavailable");
            } catch (Exception generalEx) {
                log.error("❌ Failed to fetch user by Keycloak ID {}: {}",
                        jwt.getSubject(), generalEx.getMessage(), generalEx);
                throw new BadCredentialsException("Cannot resolve user ID");
            }
        } }
}