package com.example.notification.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Slf4j
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtDecoder jwtDecoder;

    public JwtHandshakeInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String token = null;

        // 1. Vérifier l'en-tête Authorization
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
            log.info("✅ Jeton trouvé dans l'en-tête Authorization");
        }

        // 2. Si absent dans l'en-tête, vérifier le paramètre de requête "token"
        if (token == null) {
            String query = request.getURI().getQuery();
            if (StringUtils.hasText(query)) {
                String[] params = query.split("&");
                for (String param : params) {
                    if (param.startsWith("token=")) {
                        token = param.substring(6);
                        log.info("✅ Jeton trouvé dans le paramètre de requête 'token'");
                        break;
                    }
                }
            }
        }

        // 3. Si aucun jeton n'est trouvé, rejeter la connexion
        if (token == null) {
            log.warn("❌ Aucun jeton trouvé dans l'en-tête ou les paramètres de requête !");
            return false;
        }

        // 4. Valider le jeton JWT
        try {
            Jwt jwt = jwtDecoder.decode(token);
            String userId = jwt.getSubject();
            attributes.put("userId", userId);
            log.info("✅ JWT valide, userId = {}", userId);
            return true;
        } catch (JwtException e) {
            log.error("❌ Erreur de décodage JWT : {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception ex) {
        log.info("🤝 Handshake terminé");
    }
}