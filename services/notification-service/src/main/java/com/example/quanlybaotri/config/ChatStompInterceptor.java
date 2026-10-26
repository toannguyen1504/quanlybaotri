package com.example.quanlybaotri.config;

import java.util.*;
import org.springframework.messaging.*;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageHeaderAccessor;

@Component
public class ChatStompInterceptor implements ChannelInterceptor {
    private static final String TOKEN = "chat.jwt";
    private final JwtDecoder decoder;
    private final ChatWebSocketSessions sessions;
    public ChatStompInterceptor(JwtDecoder decoder, ChatWebSocketSessions sessions) {
        this.decoder = decoder; this.sessions = sessions;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) return message;
        if (accessor.getCommand() == StompCommand.CONNECT) authenticate(accessor);
        if (accessor.getCommand() == StompCommand.SEND || accessor.getCommand() == StompCommand.SUBSCRIBE) {
            validateSession(accessor);
            authorizeDestination(accessor);
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String value = accessor.getFirstNativeHeader("Authorization");
        if (!StringUtils.hasText(value) || !value.startsWith("Bearer ")) throw denied();
        String tokenValue = value.substring(7);
        Jwt jwt;
        try { jwt = decoder.decode(tokenValue); } catch (JwtException invalid) { throw denied(); }
        String uid = jwt.getClaimAsString("uid");
        if (uid == null || !"user".equals(jwt.getClaimAsString("token_type"))) throw denied();
        List<String> roleClaims = Optional.ofNullable(jwt.getClaimAsStringList("roles")).orElse(List.of());
        var auth = new UsernamePasswordAuthenticationToken(uid, tokenValue,
            roleClaims.stream().map(SimpleGrantedAuthority::new).toList());
        accessor.setUser(auth);
        if (accessor.getSessionAttributes() != null) accessor.getSessionAttributes().put(TOKEN, tokenValue);
        sessions.authenticated(accessor.getSessionId(), tokenValue, jwt.getExpiresAt());
    }

    private void validateSession(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof UsernamePasswordAuthenticationToken)) throw denied();
        Object stored = accessor.getSessionAttributes() == null ? null : accessor.getSessionAttributes().get(TOKEN);
        if (!(stored instanceof String token)) throw denied();
        try { decoder.decode(token); } catch (JwtException invalid) { throw denied(); }
    }

    private void authorizeDestination(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) throw denied();
        if (accessor.getCommand() == StompCommand.SEND &&
            !Set.of("/app/chat.send", "/app/chat.read").contains(destination)) throw denied();
        if (accessor.getCommand() == StompCommand.SUBSCRIBE &&
            !Set.of("/user/exchange/amq.direct/chat-events", "/user/exchange/amq.direct/chat-errors").contains(destination)) throw denied();
    }

    private AccessDeniedException denied() { return new AccessDeniedException("STOMP access denied"); }
}
