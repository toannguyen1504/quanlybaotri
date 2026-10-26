package com.example.quanlybaotri.config;

import java.io.IOException;
import java.time.*;
import java.util.concurrent.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

@Component
public class ChatWebSocketSessions {
    private final JwtDecoder decoder;
    private final ConcurrentMap<String, Session> sessions = new ConcurrentHashMap<>();

    public ChatWebSocketSessions(JwtDecoder decoder) { this.decoder = decoder; }
    public void opened(WebSocketSession session) { sessions.put(session.getId(), new Session(session, null, null)); }
    public void authenticated(String sessionId, String token, Instant expiresAt) {
        sessions.computeIfPresent(sessionId, (id, current) -> new Session(current.socket(), token, expiresAt));
    }
    public void closed(String sessionId) { sessions.remove(sessionId); }

    @Scheduled(fixedDelay = 30000)
    public void validate() {
        Instant now = Instant.now();
        sessions.forEach((id, current) -> {
            if (current.token() == null) return;
            try {
                if (current.expiresAt() == null || !current.expiresAt().isAfter(now)) throw new IllegalStateException("expired");
                decoder.decode(current.token());
            } catch (RuntimeException invalid) {
                try { current.socket().close(CloseStatus.POLICY_VIOLATION); } catch (IOException ignored) {}
                sessions.remove(id);
            }
        });
    }

    private record Session(WebSocketSession socket, String token, Instant expiresAt) {}
}
