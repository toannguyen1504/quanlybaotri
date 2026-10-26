package com.example.quanlybaotri.feedback.application;

import com.example.quanlybaotri.feedback.domain.SystemFeedback;
import com.example.quanlybaotri.shared.messaging.OutboxEvent;
import com.example.quanlybaotri.shared.messaging.OutboxRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class SystemFeedbackEventPublisher {

    private final OutboxRepository outbox;
    private final ObjectMapper json;

    public SystemFeedbackEventPublisher(OutboxRepository outbox, ObjectMapper json) {
        this.outbox = outbox;
        this.json = json;
    }

    public void publish(SystemFeedback feedback, UUID actorId, String action) {
        try {
            UUID eventId = UUID.randomUUID();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("feedbackId", feedback.getId());
            data.put("requesterId", feedback.getRequesterId());
            data.put("actorId", actorId);
            data.put("action", action);
            data.put("category", feedback.getCategory().name());
            data.put("status", feedback.getStatus().name());
            if (feedback.getReviewedById() != null) {
                data.put("reviewedById", feedback.getReviewedById());
            }
            String correlation = MDC.get("correlationId");
            if (correlation == null) correlation = UUID.randomUUID().toString();
            Map<String, Object> envelope = Map.of(
                "eventId", eventId,
                "eventType", "maintenance.system-feedback.changed",
                "schemaVersion", 2,
                "aggregateType", "system_feedback",
                "aggregateId", feedback.getId(),
                "occurredAt", Instant.now(),
                "producer", "maintenance-service",
                "correlationId", correlation,
                "data", data
            );
            outbox.save(new OutboxEvent(
                eventId,
                "SYSTEM_FEEDBACK",
                feedback.getId(),
                "maintenance.system-feedback.changed",
                "maintenance.system-feedback.changed",
                json.writeValueAsString(envelope)
            ));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot serialize system feedback event", ex);
        }
    }
}
