package com.example.quanlybaotri.telegram.application;

import com.example.quanlybaotri.shared.security.ServiceTokenProvider;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class MaintenanceTicketClient {
    private final RestClient client;
    private final ServiceTokenProvider tokens;

    public MaintenanceTicketClient(RestClient.Builder builder, ServiceTokenProvider tokens,
        @Value("${app.maintenance-url:http://maintenance-service:8083}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
        this.tokens = tokens;
    }

    public StartResult start(UUID ticketId, UUID technicianId) {
        StartResult result = client.post().uri("/internal/v1/tickets/{id}/telegram-start", ticketId)
            .headers(this::headers).body(new TechnicianRequest(technicianId))
            .retrieve().body(StartResult.class);
        return required(result);
    }

    public TicketPage list(UUID technicianId, int page) {
        TicketPage result = client.get().uri(uri -> uri.path("/internal/v1/telegram/tickets")
                .queryParam("technicianId", technicianId).queryParam("page", page)
                .queryParam("size", 5).build())
            .headers(this::headers).retrieve().body(TicketPage.class);
        return result == null ? new TicketPage(List.of(), 0, 0, 0) : result;
    }

    public TicketRef get(UUID ticketId, UUID technicianId) {
        return required(client.get().uri(uri -> uri.path("/internal/v1/telegram/tickets/{id}")
                .queryParam("technicianId", technicianId).build(ticketId))
            .headers(this::headers).retrieve().body(TicketRef.class));
    }

    public TicketRef action(UUID ticketId, UUID technicianId, String action, String key) {
        return required(client.post().uri("/internal/v1/telegram/tickets/{id}/{action}", ticketId, action)
            .headers(headers -> commandHeaders(headers, key)).body(new TechnicianRequest(technicianId))
            .retrieve().body(TicketRef.class));
    }

    public TicketRef reason(UUID ticketId, UUID technicianId, String action, String reason, String key) {
        return required(client.post().uri("/internal/v1/telegram/tickets/{id}/{action}", ticketId, action)
            .headers(headers -> commandHeaders(headers, key)).body(new ReasonRequest(technicianId, reason))
            .retrieve().body(TicketRef.class));
    }

    public WorkLogRef addWorkLog(UUID ticketId, UUID technicianId, String content, int minutes, String key) {
        return required(client.post().uri("/internal/v1/telegram/tickets/{id}/work-logs", ticketId)
            .headers(headers -> commandHeaders(headers, key))
            .body(new WorkLogRequest(technicianId, content, minutes))
            .retrieve().body(WorkLogRef.class));
    }

    public CostView costs(UUID ticketId, UUID technicianId) {
        return required(client.get().uri(uri -> uri.path("/internal/v1/telegram/tickets/{id}/costs")
                .queryParam("technicianId", technicianId).build(ticketId))
            .headers(this::headers).retrieve().body(CostView.class));
    }

    public CostItemRef addCost(UUID ticketId, CostRequest request, String key) {
        return required(client.post().uri("/internal/v1/telegram/tickets/{id}/cost-items", ticketId)
            .headers(headers -> commandHeaders(headers, key)).body(request)
            .retrieve().body(CostItemRef.class));
    }

    public CostItemRef updateCost(UUID ticketId, UUID itemId, CostRequest request, String key) {
        return required(client.put().uri("/internal/v1/telegram/tickets/{id}/cost-items/{itemId}", ticketId, itemId)
            .headers(headers -> commandHeaders(headers, key)).body(request)
            .retrieve().body(CostItemRef.class));
    }

    public CostView deleteCost(UUID ticketId, UUID itemId, UUID technicianId, String key) {
        return required(client.delete().uri(uri -> uri
                .path("/internal/v1/telegram/tickets/{id}/cost-items/{itemId}")
                .queryParam("technicianId", technicianId).build(ticketId, itemId))
            .headers(headers -> commandHeaders(headers, key)).retrieve().body(CostView.class));
    }

    public CostView quoteCosts(UUID ticketId, UUID technicianId, String key) {
        return required(client.post().uri("/internal/v1/telegram/tickets/{id}/costs/quote", ticketId)
            .headers(headers -> commandHeaders(headers, key)).body(new TechnicianRequest(technicianId))
            .retrieve().body(CostView.class));
    }

    public AttachmentRef upload(UUID ticketId, UUID technicianId, String type, UUID costItemId,
        TelegramClient.DownloadedFile file, String key) {
        LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("technicianId", technicianId.toString());
        body.add("type", type);
        if (costItemId != null) body.add("costItemId", costItemId.toString());
        body.add("file", new NamedFileResource(file.path(), file.filename()));
        return required(client.post().uri("/internal/v1/telegram/tickets/{id}/attachments", ticketId)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .headers(headers -> commandHeaders(headers, key)).body(body)
            .retrieve().body(AttachmentRef.class));
    }

    public List<AttachmentRef> attachments(UUID ticketId, UUID technicianId) {
        AttachmentRef[] result = client.get()
            .uri(uri -> uri.path("/internal/v1/telegram/tickets/{id}/attachments")
                .queryParam("technicianId", technicianId).build(ticketId))
            .headers(this::headers).retrieve().body(AttachmentRef[].class);
        return result == null ? List.of() : List.of(result);
    }

    public void deleteAttachment(UUID ticketId, UUID attachmentId, UUID technicianId, String key) {
        client.delete().uri(uri -> uri
                .path("/internal/v1/telegram/tickets/{id}/attachments/{attachmentId}")
                .queryParam("technicianId", technicianId).build(ticketId, attachmentId))
            .headers(headers -> commandHeaders(headers, key)).retrieve().toBodilessEntity();
    }

    private void headers(HttpHeaders headers) {
        headers.setBearerAuth(tokens.token("maintenance-service"));
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) headers.set("X-Correlation-Id", correlationId);
    }

    private void commandHeaders(HttpHeaders headers, String key) {
        headers(headers);
        headers.set("Idempotency-Key", key);
    }

    private static <T> T required(T value) {
        if (value == null) throw new IllegalStateException("Maintenance service returned no result");
        return value;
    }

    private static final class NamedFileResource extends FileSystemResource {
        private final String filename;
        NamedFileResource(Path path, String filename) { super(path); this.filename = filename; }
        @Override public String getFilename() { return filename; }
    }

    public record TechnicianRequest(UUID technicianId) {}
    public record ReasonRequest(UUID technicianId, String reason) {}
    public record WorkLogRequest(UUID technicianId, String content, int minutesSpent) {}
    public record TicketPage(List<TicketRef> content, int number, int totalPages, long totalElements) {}
    public record TicketRef(UUID id, String code, String equipmentCode, String equipmentName, String siteName,
        String title, String description, String priority, String status, String costStatus,
        BigDecimal partsTotal, BigDecimal otherTotal, BigDecimal grandTotal) {}
    public record WorkLogRef(UUID id, String content, int minutesSpent) {}
    public record CostRequest(UUID technicianId, String category, String description,
        BigDecimal quantity, String unit, BigDecimal unitCost, String incurredAt,
        String vendor, String referenceNumber) {}
    public record CostView(String status, BigDecimal partsTotal, BigDecimal otherTotal,
        BigDecimal grandTotal, boolean complete, boolean detailVisible,
        List<CostItemRef> items, String declineReason) {}
    public record CostItemRef(UUID id, String category, String sourceType, String description,
        BigDecimal quantity, String unit, BigDecimal unitCost, BigDecimal amount,
        String vendor, String referenceNumber) {}
    public record AttachmentRef(UUID id, String originalName, String type,
        boolean canDelete, UUID costItemId) {}
    public record StartResult(Outcome outcome, String status) {}
    public enum Outcome { STARTED, ALREADY_HANDLED, NO_LONGER_ASSIGNED, NOT_STARTABLE, TECHNICIAN_INELIGIBLE }
}
