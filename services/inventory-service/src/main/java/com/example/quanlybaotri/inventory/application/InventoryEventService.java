package com.example.quanlybaotri.inventory.application;

import com.example.quanlybaotri.shared.messaging.*;
import java.time.Instant;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class InventoryEventService {

    private final OutboxRepository outbox;
    private final ObjectMapper json;

    public InventoryEventService(OutboxRepository outbox, ObjectMapper json) {
        this.outbox = outbox;
        this.json = json;
    }

    public void publish(String eventType, UUID aggregateId, Map<String, Object> data) {
        try {
            UUID id = UUID.randomUUID();
            String correlation = MDC.get("correlationId");
            if (correlation == null) correlation = UUID.randomUUID().toString();
            Map<String, Object> envelope = Map.of(
                "eventId",
                id,
                "eventType",
                eventType,
                "schemaVersion",
                2,
                "aggregateType",
                "inventory",
                "aggregateId",
                aggregateId,
                "occurredAt",
                Instant.now(),
                "producer",
                "inventory-service",
                "correlationId",
                correlation,
                "data",
                data
            );
            outbox.save(
                new OutboxEvent(
                    id,
                    "INVENTORY",
                    aggregateId,
                    eventType,
                    eventType,
                    json.writeValueAsString(envelope)
                )
            );
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create inventory event", e);
        }
    }
}
