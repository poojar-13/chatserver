package com.chatapp.chatserver.websocket;

import com.chatapp.chatserver.security.JwtUtil;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;

    public JwtHandshakeInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {

        URI uri = request.getURI();
        String query = uri.getQuery(); // token=xxxxx

        if (query == null || !query.startsWith("token=")) {
            return false;
        }

        String token = query.substring(6);
        System.out.println("HANDSHAKE QUERY: " + query);
        System.out.println("TOKEN RECEIVED: " + token);
        System.out.println("VALID? " + jwtUtil.isTokenValid(token));


        if (!jwtUtil.isTokenValid(token)) {
            return false;
        }

        String username = jwtUtil.extractUsername(token);

        // 🔑 THIS IS THE KEY LINE
        attributes.put("username", username);

        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
        // nothing here
    }
}

