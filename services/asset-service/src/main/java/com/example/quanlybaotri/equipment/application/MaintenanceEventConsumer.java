package com.example.quanlybaotri.equipment.application;

import com.example.quanlybaotri.equipment.domain.EquipmentStatus;
import com.example.quanlybaotri.equipment.persistence.EquipmentRepository;
import com.example.quanlybaotri.shared.messaging.RabbitConfig;
import java.util.UUID;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.*;

@Component
public class MaintenanceEventConsumer {

    private final EquipmentRepository equipment;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public MaintenanceEventConsumer(EquipmentRepository e, JdbcTemplate j, ObjectMapper o) {
        equipment = e;
        jdbc = j;
        json = o;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE)
    @Transactional
    public void consume(String payload) throws Exception {
        JsonNode root = json.readTree(payload);
        UUID eventId = UUID.fromString(root.path("eventId").asText());
        if (
            jdbc.queryForObject(
                "select count(*) from processed_events where event_id=?",
                Integer.class,
                eventId
            ) > 0
        ) return;
        JsonNode d = root.path("data");
        if (d.has("equipmentUnderMaintenance")) {
            UUID id = UUID.fromString(d.path("equipmentId").asText());
            equipment.findById(id).ifPresent(e -> {
                boolean under = d.path("equipmentUnderMaintenance").asBoolean();
                if (under && e.isActive() && e.getStatus() != EquipmentStatus.RETIRED) e.status(
                    EquipmentStatus.UNDER_MAINTENANCE
                );
                else if (!under && e.getStatus() == EquipmentStatus.UNDER_MAINTENANCE) e.status(
                    EquipmentStatus.ACTIVE
                );
            });
        }
        jdbc.update(
            "insert into processed_events(event_id,event_type) values (?,?)",
            eventId,
            root.path("eventType").asText()
        );
    }
}
