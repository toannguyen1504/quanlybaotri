package com.example.quanlybaotri.ticket.application;

import com.example.quanlybaotri.shared.messaging.OutboxEvent;
import com.example.quanlybaotri.shared.messaging.OutboxRepository;
import com.example.quanlybaotri.ticket.domain.*;
import com.example.quanlybaotri.ticket.persistence.TicketEventRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class TicketEventService {
    private final TicketEventRepository events; private final OutboxRepository outbox; private final ObjectMapper json;
    public TicketEventService(TicketEventRepository events, OutboxRepository outbox, ObjectMapper json) {
        this.events = events; this.outbox = outbox; this.json = json;
    }

    public void record(MaintenanceTicket ticket, UUID actorId, String action, TicketStatus from, TicketStatus to,
            String description, Map<String,Object> metadata) {
        try {
            Map<String,Object> details = metadata == null ? Map.of() : metadata;
            TicketEvent event = events.save(new TicketEvent(ticket, actorId, action, from, to, description,
                    json.writeValueAsString(details)));
            Map<String,Object> data = new LinkedHashMap<>();
            data.put("ticketId", ticket.getId()); data.put("ticketCode", ticket.getCode()); data.put("action", action);
            data.put("status", ticket.getStatus().name()); data.put("priority", ticket.getPriority().name());
            data.put("actorId", actorId); data.put("requesterId", ticket.getRequesterId());
            data.put("assigneeId", ticket.getAssigneeId()); data.put("equipmentId", ticket.getEquipmentId());
            data.put("description", description); data.put("submittedAt", ticket.getSubmittedAt());
            data.put("resolutionDueAt", ticket.getResolutionDueAt()); data.put("resolvedAt", ticket.getResolvedAt());
            data.put("metadata", details); data.putAll(details);
            String correlation = MDC.get("correlationId");
            if (correlation == null) correlation = UUID.randomUUID().toString();
            Map<String,Object> envelope = Map.of("eventId", event.getId(), "eventType", "maintenance.ticket.changed",
                    "schemaVersion", 2, "aggregateType", "ticket", "aggregateId", ticket.getId(),
                    "occurredAt", Instant.now(), "producer", "maintenance-service", "correlationId", correlation,
                    "data", data);
            outbox.save(new OutboxEvent(event.getId(), "TICKET", ticket.getId(), "maintenance.ticket.changed",
                    "maintenance.ticket.changed", json.writeValueAsString(envelope)));
        } catch (Exception ex) { throw new IllegalStateException("Cannot serialize maintenance event", ex); }
    }
}
