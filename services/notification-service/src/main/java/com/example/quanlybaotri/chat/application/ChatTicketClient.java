package com.example.quanlybaotri.chat.application;

import com.example.quanlybaotri.shared.security.ServiceTokenProvider;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ChatTicketClient {
    private final RestClient client;
    private final ServiceTokenProvider tokens;

    public ChatTicketClient(RestClient.Builder builder, ServiceTokenProvider tokens,
        @Value("${app.maintenance-url:http://maintenance-service:8083}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
        this.tokens = tokens;
    }

    public ChatContext get(UUID ticketId, String userToken) {
        ChatContext context = client.get().uri("/internal/v1/tickets/{id}/chat-context", ticketId)
            .headers(headers -> headers(headers, userToken)).retrieve().body(ChatContext.class);
        if (context == null) throw new IllegalStateException("Maintenance service returned no chat context");
        return context;
    }

    public List<ChatContext> resolve(Collection<UUID> ticketIds, String userToken) {
        if (ticketIds == null || ticketIds.isEmpty()) return List.of();
        ChatContext[] contexts = client.post().uri("/internal/v1/tickets/chat-contexts")
            .headers(headers -> headers(headers, userToken))
            .body(new ResolveRequest(new ArrayList<>(new LinkedHashSet<>(ticketIds))))
            .retrieve().body(ChatContext[].class);
        return contexts == null ? List.of() : List.of(contexts);
    }

    private void headers(org.springframework.http.HttpHeaders headers, String userToken) {
        headers.setBearerAuth(tokens.token("maintenance-service"));
        headers.set("X-User-Token", userToken);
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) headers.set("X-Correlation-Id", correlationId);
    }

    private record ResolveRequest(List<UUID> ids) {}

    public record ChatContext(UUID ticketId, String ticketCode, String ticketTitle, String status,
        UUID requesterId, UUID assigneeId, boolean writable) {}
}
