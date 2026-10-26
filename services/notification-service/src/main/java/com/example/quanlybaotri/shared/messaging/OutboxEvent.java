package com.example.quanlybaotri.shared.messaging;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id private UUID id;
    @Column(name = "aggregate_type", nullable = false, length = 50) private String aggregateType;
    @Column(name = "aggregate_id", nullable = false) private UUID aggregateId;
    @Column(name = "event_type", nullable = false, length = 80) private String eventType;
    @Column(name = "routing_key", nullable = false, length = 100) private String routingKey;
    @Column(name = "payload_json", nullable = false, columnDefinition = "text") private String payloadJson;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(nullable = false) private int attempts;
    @Column(name = "last_error", length = 1000) private String lastError;

    protected OutboxEvent() {}
    public OutboxEvent(UUID id, String aggregateType, UUID aggregateId, String eventType, String routingKey, String payload) {
        this.id = id; this.aggregateType = aggregateType; this.aggregateId = aggregateId;
        this.eventType = eventType; this.routingKey = routingKey; this.payloadJson = payload;
        this.occurredAt = Instant.now();
    }
    public void published() { publishedAt = Instant.now(); lastError = null; }
    public void failed(String value) { attempts++; lastError = value == null ? null : value.substring(0, Math.min(1000, value.length())); }
    public UUID getId() { return id; }
    public String getRoutingKey() { return routingKey; }
    public String getPayloadJson() { return payloadJson; }
}
