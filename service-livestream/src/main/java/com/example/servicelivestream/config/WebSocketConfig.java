package com.example.servicelivestream.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

import java.util.Map;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtDecoder jwtDecoder;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // ✅ Endpoint principal pour le chat avec interceptor de handshake
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("http://localhost:4200")
                .addInterceptors(new HandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(
                            ServerHttpRequest request,
                            ServerHttpResponse response,
                            WebSocketHandler wsHandler,
                            Map<String, Object> attributes
                    ) throws Exception {
                        log.info("🤝 WebSocket handshake from: {}", request.getRemoteAddress());
                        log.info("🔍 Request URI: {}", request.getURI());
                        log.info("🔍 Request headers: {}", request.getHeaders());
                        return true; // Autoriser la connexion
                    }

                    @Override
                    public void afterHandshake(
                            ServerHttpRequest request,
                            ServerHttpResponse response,
                            WebSocketHandler wsHandler,
                            Exception exception
                    ) {
                        if (exception == null) {
                            log.info("✅ WebSocket handshake completed successfully");
                        } else {
                            log.error("❌ WebSocket handshake failed", exception);
                        }
                    }
                });

        // ✅ Endpoint SockJS fallback
        registry.addEndpoint("/ws/websocket")
                .setAllowedOriginPatterns("http://localhost:4200")
                .addInterceptors(new HandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(
                            ServerHttpRequest request,
                            ServerHttpResponse response,
                            WebSocketHandler wsHandler,
                            Map<String, Object> attributes
                    ) throws Exception {
                        log.info("🤝 SockJS handshake from: {}", request.getRemoteAddress());
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
                            log.info("✅ SockJS handshake completed successfully");
                        } else {
                            log.error("❌ SockJS handshake failed", exception);
                        }
                    }
                })
                .withSockJS()
                .setSessionCookieNeeded(false);

        log.info("✅ WebSocket endpoints registered:");
        log.info("   - /ws (primary endpoint)");
        log.info("   - /ws/websocket (SockJS fallback)");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // ✅ Configuration du broker simple
        registry.enableSimpleBroker("/queue", "/topic")
                .setHeartbeatValue(new long[]{10000, 10000})
                .setTaskScheduler(heartBeatScheduler());

        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");

        log.info("✅ Message broker configured:");
        log.info("   - SimpleBroker: /queue, /topic");
        log.info("   - App destinations: /app");
        log.info("   - User destinations: /user");
        log.info("   - Heartbeat: 10s");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                        message, StompHeaderAccessor.class
                );

                if (accessor != null) {
                    log.debug("📨 Incoming message: command={}, destination={}",
                            accessor.getCommand(), accessor.getDestination());

                    if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                        log.info("🔐 Processing CONNECT command");

                        String token = accessor.getFirstNativeHeader("Authorization");
                        if (token == null || !token.startsWith("Bearer ")) {
                            log.error("❌ Missing or invalid Authorization header");
                            log.debug("📋 Available headers: {}", accessor.toNativeHeaderMap());
                            throw new BadCredentialsException("Missing or invalid Authorization header");
                        }

                        try {
                            String jwtToken = token.substring(7);
                            log.debug("🔍 Decoding JWT token (length: {})", jwtToken.length());

                            Jwt jwt = jwtDecoder.decode(jwtToken);
                            JwtAuthenticationToken authToken = new JwtAuthenticationToken(jwt);
                            accessor.setUser(authToken);

                            log.info("✅ WebSocket authentication successful for user: {}", jwt.getSubject());
                            log.debug("👤 User authorities: {}", authToken.getAuthorities());

                        } catch (JwtException e) {
                            log.error("❌ JWT validation failed: {}", e.getMessage());
                            log.debug("🔍 JWT error details", e);
                            throw new BadCredentialsException("Invalid JWT: " + e.getMessage(), e);
                        }
                    }
                }
                return message;
            }
        });

        // ✅ Configuration des threads
        registration.taskExecutor().corePoolSize(8).maxPoolSize(16);
        log.info("✅ Client inbound channel configured with 8-16 threads");
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor().corePoolSize(8).maxPoolSize(16);
        log.info("✅ Client outbound channel configured with 8-16 threads");
    }

    @Bean
    public TaskScheduler heartBeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("websocket-heartbeat-");
        scheduler.initialize();
        log.info("✅ WebSocket heartbeat scheduler initialized");
        return scheduler;
    }
}