package com.example.gateway.configuration;

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

        // Handle all WebSocket and SockJS requests
        if (path.startsWith("/ws/notifications")) {
            String token = request.getQueryParams().getFirst("token");
            if (token != null && !token.isEmpty()) {
                ServerHttpRequest mutatedRequest = request.mutate()
                        .header("Authorization", "Bearer " + token)
                        .build();
                return chain.filter(exchange.mutate().request(mutatedRequest).build());
            }
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -1; // s'assurer que ce filtre est prioritaire
    }

}