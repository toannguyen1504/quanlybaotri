package com.example.quanlybaotri.telegram.application;

import com.example.quanlybaotri.shared.security.ServiceTokenProvider;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class MaintenanceTicketClient {

    private final RestClient client;
    private final ServiceTokenProvider tokens;

    public MaintenanceTicketClient(
        RestClient.Builder builder,
        ServiceTokenProvider tokens,
        @Value("${app.maintenance-url:http://maintenance-service:8083}") String baseUrl
    ) {
        this.client = builder.baseUrl(baseUrl).build();
        this.tokens = tokens;
    }

    public StartResult start(UUID ticketId, UUID technicianId) {
        StartResult result = client
            .post()
            .uri("/internal/v1/tickets/{id}/telegram-start", ticketId)
            .headers(headers -> {
                headers.setBearerAuth(tokens.token("maintenance-service"));
                String correlationId = MDC.get("correlationId");
                if (correlationId != null) headers.set("X-Correlation-Id", correlationId);
            })
            .body(new StartRequest(technicianId))
            .retrieve()
            .body(StartResult.class);
        if (result == null) throw new IllegalStateException(
            "Maintenance service returned no Telegram start result"
        );
        return result;
    }

    private record StartRequest(UUID technicianId) {}

    public record StartResult(Outcome outcome, String status) {}

    public enum Outcome {
        STARTED,
        ALREADY_HANDLED,
        NO_LONGER_ASSIGNED,
        NOT_STARTABLE,
        TECHNICIAN_INELIGIBLE,
    }
}
