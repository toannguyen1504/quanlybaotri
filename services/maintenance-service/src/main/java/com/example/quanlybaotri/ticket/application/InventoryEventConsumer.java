package com.example.quanlybaotri.ticket.application;

import com.example.quanlybaotri.shared.messaging.RabbitConfig;
import com.example.quanlybaotri.ticket.persistence.TicketRepository;
import com.example.quanlybaotri.cost.application.CostService;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class InventoryEventConsumer {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TicketRepository tickets;
    private final TicketEventService events;
    private final CostService costs;

    public InventoryEventConsumer(
        JdbcTemplate jdbc,
        ObjectMapper json,
        TicketRepository tickets,
        TicketEventService events,
        CostService costs
    ) {
        this.jdbc = jdbc;
        this.json = json;
        this.tickets = tickets;
        this.events = events;
        this.costs = costs;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE)
    @Transactional
    public void consume(String payload) throws Exception {
        JsonNode root = json.readTree(payload);
        UUID eventId = UUID.fromString(root.path("eventId").asText());
        if (
            Boolean.TRUE.equals(
                jdbc.queryForObject(
                    "select exists(select 1 from processed_events where event_id=?)",
                    Boolean.class,
                    eventId
                )
            )
        ) return;
        if (!"inventory.part.used".equals(root.path("eventType").asText())) return;
        JsonNode data = root.path("data");
        UUID ticketId = UUID.fromString(data.path("ticketId").asText());
        UUID actorId =
            data.path("usedById").isMissingNode() || data.path("usedById").isNull()
                ? null
                : UUID.fromString(data.path("usedById").asText());
        BigDecimal quantity = new BigDecimal(data.path("quantity").asText());
        BigDecimal unitCost = new BigDecimal(data.path("unitCost").asText());
        var ticket = tickets
            .findById(ticketId)
            .orElseThrow(() -> new IllegalStateException("Ticket not found: " + ticketId));
        UUID usageId = UUID.fromString(root.path("aggregateId").asText());
        costs.addPart(
            ticketId,
            usageId,
            "Linh kiện " + data.path("partCode").asText() + " - " + data.path("partName").asText(),
            quantity,
            data.path("unit").asText("ĐƠN VỊ"),
            unitCost,
            root.path("occurredAt").isTextual() ? java.time.Instant.parse(root.path("occurredAt").asText()) : java.time.Instant.now(),
            actorId
        );
        events.record(
            ticket,
            actorId,
            "PART_USED",
            ticket.getStatus(),
            ticket.getStatus(),
            "Đã sử dụng linh kiện",
            Map.of(
                "inventoryEventId",
                eventId,
                "partId",
                data.path("partId").asText(),
                "quantity",
                quantity
            )
        );
        jdbc.update(
            "insert into processed_events(event_id,event_type) values (?,?)",
            eventId,
            "inventory.part.used"
        );
    }
}
