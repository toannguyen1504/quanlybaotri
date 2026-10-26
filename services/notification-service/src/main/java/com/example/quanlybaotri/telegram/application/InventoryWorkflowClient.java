package com.example.quanlybaotri.telegram.application;

import com.example.quanlybaotri.shared.security.ServiceTokenProvider;
import java.math.BigDecimal;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class InventoryWorkflowClient {
    private final RestClient client;
    private final ServiceTokenProvider tokens;

    public InventoryWorkflowClient(RestClient.Builder builder, ServiceTokenProvider tokens,
        @Value("${app.inventory-url:http://inventory-service:8084}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
        this.tokens = tokens;
    }

    public PartPage search(String query, int page) {
        PartPage result = client.get().uri(uri -> uri.path("/internal/v1/telegram/parts")
                .queryParam("q", query == null ? "" : query).queryParam("page", page)
                .queryParam("size", 5).build())
            .headers(this::headers).retrieve().body(PartPage.class);
        return result == null ? new PartPage(List.of(), 0, 0, 0) : result;
    }

    public PartUseRef use(UUID ticketId, UUID technicianId, UUID partId,
        BigDecimal quantity, String key) {
        PartUseRef result = client.post().uri("/internal/v1/telegram/tickets/{id}/parts", ticketId)
            .headers(headers -> {
                headers(headers);
                headers.set("Idempotency-Key", key);
            })
            .body(new UseRequest(technicianId, partId, quantity))
            .retrieve().body(PartUseRef.class);
        if (result == null) throw new IllegalStateException("Inventory service returned no result");
        return result;
    }

    private void headers(HttpHeaders headers) {
        headers.setBearerAuth(tokens.token("inventory-service"));
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) headers.set("X-Correlation-Id", correlationId);
    }

    public record PartPage(List<PartRef> content, int number, int totalPages, long totalElements) {}
    public record PartRef(UUID id, String code, String name, String unit,
        BigDecimal currentStock, BigDecimal defaultUnitCost, boolean active) {}
    public record UseRequest(UUID technicianId, UUID partId, BigDecimal quantity) {}
    public record PartUseRef(UUID id, UUID partId, String partCode, String partName,
        BigDecimal quantity, BigDecimal unitCost) {}
}
