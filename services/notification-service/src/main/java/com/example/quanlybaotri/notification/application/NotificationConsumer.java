package com.example.quanlybaotri.notification.application;

import com.example.quanlybaotri.config.RabbitConfig;
import com.example.quanlybaotri.identity.client.IdentityClient;
import com.example.quanlybaotri.notification.domain.*;
import com.example.quanlybaotri.notification.persistence.*;
import com.example.quanlybaotri.telegram.domain.TelegramDelivery;
import com.example.quanlybaotri.telegram.persistence.TelegramDeliveryRepository;
import com.example.quanlybaotri.telegram.persistence.TelegramLinkRepository;
import java.util.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.*;

@Component
public class NotificationConsumer {

    private final ProcessedMessageRepository processed;
    private final NotificationRepository notifications;
    private final IdentityClient identities;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;
    private final TelegramLinkRepository telegramLinks;
    private final TelegramDeliveryRepository telegramDeliveries;

    public NotificationConsumer(
        ProcessedMessageRepository p,
        NotificationRepository n,
        IdentityClient i,
        ObjectMapper j,
        TransactionTemplate t,
        TelegramLinkRepository telegramLinks,
        TelegramDeliveryRepository telegramDeliveries
    ) {
        processed = p;
        notifications = n;
        identities = i;
        json = j;
        transactions = t;
        this.telegramLinks = telegramLinks;
        this.telegramDeliveries = telegramDeliveries;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void consume(String payload) throws Exception {
        JsonNode root = json.readTree(payload);
        int schema = root.path("schemaVersion").asInt();
        if (schema < 1 || schema > 2) throw new IllegalArgumentException(
            "Unsupported event schema"
        );
        UUID eventId = UUID.fromString(root.path("eventId").asText());
        String type = root.path("eventType").asText();
        JsonNode data = root.path("data");
        NotificationCommand command = switch (type) {
            case "maintenance.ticket.changed" -> ticketChanged(eventId, data);
            case "inventory.part.low-stock" -> lowStock(eventId, data);
            default -> new NotificationCommand(
                eventId,
                Set.of(),
                "IGNORED",
                "",
                "",
                null,
                null,
                null
            );
        };
        transactions.executeWithoutResult(tx -> {
            if (processed.existsById(eventId)) return;
            for (UUID id : command.recipients()) {
                Notification notification = notifications.save(
                    new Notification(
                        id,
                        eventId,
                        command.type(),
                        command.title(),
                        command.message(),
                        command.referenceType(),
                        command.referenceId()
                    )
                );
                if (
                    command.telegramMessage() != null &&
                    telegramLinks.findByUserIdAndActiveTrue(id).isPresent()
                ) telegramDeliveries.save(
                    new TelegramDelivery(
                        notification.getId(),
                        id,
                        command.telegramMessage(),
                        command.referenceId()
                    )
                );
            }
            processed.save(new ProcessedMessage(eventId));
        });
    }

    private NotificationCommand ticketChanged(UUID eventId, JsonNode d) {
        Set<UUID> recipients = new LinkedHashSet<>();
        add(recipients, nullableUuid(d, "requesterId"));
        add(recipients, nullableUuid(d, "assigneeId"));
        recipients.remove(nullableUuid(d, "actorId"));
        if ("SUBMITTED".equals(text(d, "action"))) recipients.addAll(
            identities.enabledByRoles(Set.of("ADMIN", "MANAGER"))
        );
        return new NotificationCommand(
            eventId,
            recipients,
            text(d, "action"),
            "Phiếu " + text(d, "ticketCode"),
            text(d, "description"),
            "TICKET",
            uuid(d, "ticketId"),
            telegramTicketMessage(d)
        );
    }

    private NotificationCommand lowStock(UUID eventId, JsonNode d) {
        Set<String> roles = Set.of(
            text(d, "recipientRole").isBlank() ? "MANAGER" : text(d, "recipientRole")
        );
        Set<UUID> recipients = identities.enabledByRoles(roles);
        String message =
            "Linh kiện " +
            text(d, "partCode") +
            " đã chạm mức tồn tối thiểu (còn " +
            text(d, "balance") +
            ")";
        return new NotificationCommand(
            eventId,
            recipients,
            "PART_LOW_STOCK",
            "Linh kiện sắp hết",
            message,
            "PART",
            uuid(d, "partId"),
            null
        );
    }

    private String telegramTicketMessage(JsonNode d) {
        String message =
            "🔧 Phiếu " +
            text(d, "ticketCode") +
            "\nHành động: " +
            text(d, "action") +
            "\nTrạng thái: " +
            text(d, "status") +
            "\nƯu tiên: " +
            text(d, "priority") +
            "\n\n" +
            text(d, "description");
        return message.length() <= 2000 ? message : message.substring(0, 1997) + "...";
    }

    private static void add(Set<UUID> s, UUID v) {
        if (v != null) s.add(v);
    }

    private static String text(JsonNode n, String k) {
        return n.path(k).asText("");
    }

    private static UUID uuid(JsonNode n, String k) {
        return UUID.fromString(text(n, k));
    }

    private static UUID nullableUuid(JsonNode n, String k) {
        String v = text(n, k);
        return v.isBlank() ? null : UUID.fromString(v);
    }

    private record NotificationCommand(
        UUID eventId,
        Set<UUID> recipients,
        String type,
        String title,
        String message,
        String referenceType,
        UUID referenceId,
        String telegramMessage
    ) {}
}
