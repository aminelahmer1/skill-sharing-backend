package com.example.gateway.configuration;
/*
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class WebSocketTokenFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // Handle WebSocket requests
        if (path.startsWith("/ws/notifications")) {
            // Check if Authorization header is already present
            String authHeader = request.getHeaders().getFirst("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                // Header is already set correctly, proceed
                return chain.filter(exchange);
            }
            // Optionally handle query parameter for backward compatibility
            String token = request.getQueryParams().getFirst("token");
            if (token != null && !token.isEmpty()) {
                ServerHttpRequest mutatedRequest = request.mutate()
                        .header("Authorization", "Bearer " + token)
                        .build();
                return chain.filter(exchange.mutate().request(mutatedRequest).build());
            }
            // Log warning if no token is found
            System.out.println("No Authorization header or token query parameter found for WebSocket request");
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -1; // Keep high precedence
    }
}*/