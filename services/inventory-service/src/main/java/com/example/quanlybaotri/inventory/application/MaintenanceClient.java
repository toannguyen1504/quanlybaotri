package com.example.quanlybaotri.inventory.application;

import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.shared.security.ServiceTokenProvider;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class MaintenanceClient {

    private final RestClient client;
    private final ServiceTokenProvider tokens;

    public MaintenanceClient(
        RestClient.Builder b,
        ServiceTokenProvider tokens,
        @Value("${app.maintenance-url:http://maintenance-service:8083}") String base
    ) {
        client = b.baseUrl(base).build();
        this.tokens = tokens;
    }

    public TicketRef authorize(UUID ticketId, String operation, String userToken) {
        try {
            return client
                .get()
                .uri(u ->
                    u
                        .path("/internal/v1/tickets/{id}/access")
                        .queryParam("operation", operation)
                        .build(ticketId)
                )
                .headers(h -> {
                    serviceHeaders(h);
                    h.set("X-User-Token", userToken);
                })
                .retrieve()
                .body(TicketRef.class);
        } catch (HttpClientErrorException.Forbidden e) {
            throw ApiException.forbidden("Không có quyền truy cập phiếu");
        } catch (HttpClientErrorException.NotFound e) {
            throw ApiException.notFound("Không tìm thấy phiếu");
        } catch (RestClientException e) {
            throw unavailable();
        }
    }

    public Map<UUID, TicketRef> resolve(Collection<UUID> ids) {
        Set<UUID> u = new LinkedHashSet<>(ids);
        u.remove(null);
        if (u.isEmpty()) return Map.of();
        try {
            TicketRef[] r = client
                .post()
                .uri("/internal/v1/tickets/resolve")
                .headers(this::serviceHeaders)
                .body(new ResolveRequest(List.copyOf(u)))
                .retrieve()
                .body(TicketRef[].class);
            Map<UUID, TicketRef> out = new HashMap<>();
            if (r != null) for (TicketRef t : r) out.put(t.ticketId(), t);
            return out;
        } catch (RestClientException e) {
            throw unavailable();
        }
    }

    private void serviceHeaders(org.springframework.http.HttpHeaders h) {
        h.setBearerAuth(tokens.token("maintenance-service"));
        String c = MDC.get("correlationId");
        if (c != null) h.set("X-Correlation-Id", c);
    }

    private ApiException unavailable() {
        return new ApiException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "DEPENDENCY_UNAVAILABLE",
            "Maintenance service không sẵn sàng"
        );
    }

    public record ResolveRequest(List<UUID> ids) {}

    public record TicketRef(
        UUID ticketId,
        String ticketCode,
        String status,
        UUID assigneeId,
        boolean allowed
    ) {}
}
