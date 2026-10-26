package com.example.quanlybaotri.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class ChatStompInterceptorTest {
    private JwtDecoder decoder;
    private ChatWebSocketSessions sessions;
    private ChatStompInterceptor interceptor;
    private MessageChannel channel;

    @BeforeEach
    void setUp() {
        decoder = mock(JwtDecoder.class);
        sessions = mock(ChatWebSocketSessions.class);
        interceptor = new ChatStompInterceptor(decoder, sessions);
        channel = mock(MessageChannel.class);
    }

    @Test
    void authenticatesConnectFromBearerHeader() {
        UUID userId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(300);
        Jwt jwt = userJwt(userId, expiresAt);
        when(decoder.decode("access-token")).thenReturn(jwt);
        StompHeaderAccessor accessor = accessor(StompCommand.CONNECT, null);
        accessor.setNativeHeader("Authorization", "Bearer access-token");

        Message<?> result = interceptor.preSend(message(accessor), channel);
        StompHeaderAccessor resultHeaders = StompHeaderAccessor.wrap(result);

        assertThat(resultHeaders.getUser()).isNotNull();
        assertThat(resultHeaders.getUser().getName()).isEqualTo(userId.toString());
        verify(sessions).authenticated("session-1", "access-token", expiresAt);
    }

    @Test
    void rejectsExpiredOrRevokedConnectToken() {
        when(decoder.decode("invalid")).thenThrow(new JwtException("expired"));
        StompHeaderAccessor accessor = accessor(StompCommand.CONNECT, null);
        accessor.setNativeHeader("Authorization", "Bearer invalid");

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), channel))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectsClientSendToBrokerDestination() {
        StompHeaderAccessor accessor = authenticatedAccessor(StompCommand.SEND, "/exchange/amq.direct/chat-events");

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), channel))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void acceptsOnlyOwnChatUserSubscriptions() {
        StompHeaderAccessor allowed = authenticatedAccessor(StompCommand.SUBSCRIBE,
            "/user/exchange/amq.direct/chat-events");
        StompHeaderAccessor forbidden = authenticatedAccessor(StompCommand.SUBSCRIBE,
            "/user/another-user/exchange/amq.direct/chat-events");

        assertThat(interceptor.preSend(message(allowed), channel)).isNotNull();
        assertThatThrownBy(() -> interceptor.preSend(message(forbidden), channel))
            .isInstanceOf(AccessDeniedException.class);
    }

    private StompHeaderAccessor authenticatedAccessor(StompCommand command, String destination) {
        UUID userId = UUID.randomUUID();
        Jwt jwt = userJwt(userId, Instant.now().plusSeconds(300));
        when(decoder.decode("access-token")).thenReturn(jwt);
        StompHeaderAccessor accessor = accessor(command, destination);
        accessor.setUser(new UsernamePasswordAuthenticationToken(userId.toString(), "access-token", List.of()));
        accessor.getSessionAttributes().put("chat.jwt", "access-token");
        return accessor;
    }

    private StompHeaderAccessor accessor(StompCommand command, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId("session-1");
        accessor.setSessionAttributes(new HashMap<>());
        if (destination != null) accessor.setDestination(destination);
        accessor.setLeaveMutable(true);
        return accessor;
    }

    private Message<byte[]> message(StompHeaderAccessor accessor) {
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Jwt userJwt(UUID userId, Instant expiresAt) {
        return Jwt.withTokenValue("access-token")
            .header("alg", "none")
            .subject("user")
            .issuedAt(Instant.now())
            .expiresAt(expiresAt)
            .claim("uid", userId.toString())
            .claim("token_type", "user")
            .claim("roles", List.of("ROLE_REQUESTER"))
            .build();
    }
}
