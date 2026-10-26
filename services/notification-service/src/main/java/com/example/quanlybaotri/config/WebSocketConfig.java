package com.example.quanlybaotri.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final ChatStompInterceptor interceptor;
    private final ChatWebSocketSessions sessions;
    private final String relayHost;
    private final int relayPort;
    private final String relayUser;
    private final String relayPassword;
    private final String[] allowedOrigins;

    public WebSocketConfig(ChatStompInterceptor interceptor, ChatWebSocketSessions sessions,
        @Value("${spring.rabbitmq.host:rabbitmq}") String relayHost,
        @Value("${app.chat.stomp-port:61613}") int relayPort,
        @Value("${spring.rabbitmq.username:maintenance_app}") String relayUser,
        @Value("${spring.rabbitmq.password:maintenance_dev}") String relayPassword,
        @Value("${app.chat.allowed-origin-patterns:http://localhost:*}") String origins) {
        this.interceptor = interceptor; this.sessions = sessions; this.relayHost = relayHost; this.relayPort = relayPort;
        this.relayUser = relayUser; this.relayPassword = relayPassword;
        this.allowedOrigins = List.of(origins.split(",")).stream().map(String::trim).toArray(String[]::new);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/chat").setAllowedOriginPatterns(allowedOrigins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        registry.enableStompBrokerRelay("/queue", "/topic", "/exchange")
            .setRelayHost(relayHost).setRelayPort(relayPort)
            .setClientLogin(relayUser).setClientPasscode(relayPassword)
            .setSystemLogin(relayUser).setSystemPasscode(relayPassword)
            .setSystemHeartbeatSendInterval(10000)
            .setSystemHeartbeatReceiveInterval(10000)
            .setUserDestinationBroadcast("/topic/chat-unresolved-user")
            .setUserRegistryBroadcast("/topic/chat-user-registry");
    }

    @Override
    public void configureClientInboundChannel(org.springframework.messaging.simp.config.ChannelRegistration registration) {
        registration.interceptors(interceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(handler -> new org.springframework.web.socket.handler.WebSocketHandlerDecorator(handler) {
            @Override public void afterConnectionEstablished(org.springframework.web.socket.WebSocketSession session) throws Exception {
                sessions.opened(session);
                super.afterConnectionEstablished(session);
            }
            @Override public void afterConnectionClosed(org.springframework.web.socket.WebSocketSession session,
                org.springframework.web.socket.CloseStatus closeStatus) throws Exception {
                sessions.closed(session.getId());
                super.afterConnectionClosed(session, closeStatus);
            }
        });
    }
}
