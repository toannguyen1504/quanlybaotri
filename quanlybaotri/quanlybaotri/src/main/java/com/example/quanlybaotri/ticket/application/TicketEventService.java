package com.example.quanlybaotri.ticket.application;

import com.example.quanlybaotri.identity.domain.UserAccount;
import com.example.quanlybaotri.shared.messaging.OutboxEvent;
import com.example.quanlybaotri.shared.messaging.OutboxRepository;
import com.example.quanlybaotri.ticket.domain.*;
import com.example.quanlybaotri.ticket.persistence.TicketEventRepository;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class TicketEventService {
    private final TicketEventRepository events;
    private final OutboxRepository outbox;
    private final ObjectMapper json;
    private final StringRedisTemplate redis;

    public TicketEventService(TicketEventRepository events, OutboxRepository outbox, ObjectMapper json,
            StringRedisTemplate redis) {
        this.events = events;
        this.outbox = outbox;
        this.json = json;
        this.redis = redis;
    }

    public void record(MaintenanceTicket ticket, UserAccount actor, String type, TicketStatus from, TicketStatus to,
            String description, Map<String, Object> metadata) {
        try {
            TicketEvent event = events.save(new TicketEvent(ticket, actor, type, from, to, description,
                    json.writeValueAsString(metadata == null ? Map.of() : metadata)));
            String payload = json
                    .writeValueAsString(Map.of("eventId", event.getId(), "ticketId", ticket.getId(), "ticketCode",
                            ticket.getCode(), "type", type, "actorId", actor == null ? "" : actor.getId().toString(),
                            "assigneeId", ticket.getAssignee() == null ? "" : ticket.getAssignee().getId().toString(),
                            "requesterId", ticket.getRequester().getId().toString(), "description", description));
            outbox.save(new OutboxEvent(event.getId(), "TICKET", ticket.getId(), type, routingKey(type), payload));
            try {
                redis.opsForValue().increment("cache:dashboard:version");
            } catch (RuntimeException ignored) {
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot serialize domain event", ex);
        }
    }

    private String routingKey(String type) {
        if (type.startsWith("SLA_"))
            return "sla." + type.substring(4).toLowerCase().replace('_', '-');
        if (type.equals("PART_LOW_STOCK"))
            return "part.low-stock";
        return "ticket." + type.toLowerCase().replace('_', '-');
    }
}
