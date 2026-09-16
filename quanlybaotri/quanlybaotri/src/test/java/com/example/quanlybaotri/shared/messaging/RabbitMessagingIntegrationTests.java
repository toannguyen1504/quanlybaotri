package com.example.quanlybaotri.shared.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.quanlybaotri.equipment.domain.Equipment;
import com.example.quanlybaotri.equipment.domain.EquipmentCategory;
import com.example.quanlybaotri.equipment.persistence.EquipmentCategoryRepository;
import com.example.quanlybaotri.equipment.persistence.EquipmentRepository;
import com.example.quanlybaotri.identity.domain.RoleName;
import com.example.quanlybaotri.identity.domain.UserAccount;
import com.example.quanlybaotri.identity.persistence.RoleRepository;
import com.example.quanlybaotri.identity.persistence.UserRepository;
import com.example.quanlybaotri.notification.persistence.NotificationRepository;
import com.example.quanlybaotri.notification.persistence.ProcessedMessageRepository;
import com.example.quanlybaotri.ticket.domain.MaintenanceTicket;
import com.example.quanlybaotri.ticket.domain.TicketEvent;
import com.example.quanlybaotri.ticket.domain.TicketPriority;
import com.example.quanlybaotri.ticket.domain.TicketStatus;
import com.example.quanlybaotri.ticket.persistence.TicketEventRepository;
import com.example.quanlybaotri.ticket.persistence.TicketRepository;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "spring.rabbitmq.username=maintenance_app",
        "spring.rabbitmq.password=MaintenanceRabbit@123",
        "spring.rabbitmq.virtual-host=quanlybaotri",
        "spring.rabbitmq.listener.simple.auto-startup=true",
        "app.outbox.initial-delay-ms=3600000",
        "app.outbox.delay-ms=3600000"
})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "RABBITMQ_IT", matches = "(?i)true")
class RabbitMessagingIntegrationTests {
    @Autowired
    AmqpAdmin amqpAdmin;
    @Autowired
    RabbitTemplate rabbit;
    @Autowired
    OutboxRelay relay;
    @Autowired
    OutboxRepository outbox;
    @Autowired
    TicketEventRepository events;
    @Autowired
    TicketRepository tickets;
    @Autowired
    NotificationRepository notifications;
    @Autowired
    ProcessedMessageRepository processedMessages;
    @Autowired
    UserRepository users;
    @Autowired
    RoleRepository roles;
    @Autowired
    EquipmentCategoryRepository categories;
    @Autowired
    EquipmentRepository equipment;
    @Autowired
    EntityManager entityManager;

    private TransactionTemplate transactions;

    @Autowired
    void transactionManager(PlatformTransactionManager manager) {
        transactions = new TransactionTemplate(manager);
    }

    @Test
    void publishesConsumesReturnsAndDeadLettersWithARealBroker() throws Exception {
        assertThat(amqpAdmin.getQueueProperties(RabbitConfig.NOTIFICATION_QUEUE)).isNotNull();
        assertThat(amqpAdmin.getQueueProperties(RabbitConfig.DLQ)).isNotNull();
        amqpAdmin.purgeQueue(RabbitConfig.DLQ, false);

        Fixture fixture = createFixture();
        UUID unroutableId = UUID.randomUUID();
        try {
            relay.publish();

            await(() -> processedMessages.existsById(fixture.eventId())
                    && notifications.countByUserIdAndReadAtIsNull(fixture.requesterId()) == 1, 10_000);
            assertThat(outbox.findById(fixture.eventId()).orElseThrow().getPublishedAt()).isNotNull();

            Message poison = new Message("{}".getBytes(StandardCharsets.UTF_8));
            poison.getMessageProperties().setHeader("eventId", UUID.randomUUID().toString());
            rabbit.send(RabbitConfig.EXCHANGE, "ticket.integration-poison", poison);
            assertThat(rabbit.receive(RabbitConfig.DLQ, 10_000)).isNotNull();

            OutboxEvent unroutable = new OutboxEvent(unroutableId, "TICKET", fixture.ticketId(), "TEST",
                    "unknown.integration-route", "{}");
            transactions.executeWithoutResult(status -> outbox.save(unroutable));
            relay.publish();

            OutboxEvent failed = outbox.findById(unroutableId).orElseThrow();
            assertThat(failed.getPublishedAt()).isNull();
            assertThat(failed.getAttempts()).isEqualTo(1);
            assertThat(failed.getLastError()).contains("NO_ROUTE");
        }
        finally {
            cleanup(fixture, unroutableId);
            amqpAdmin.purgeQueue(RabbitConfig.DLQ, false);
        }
    }

    private Fixture createFixture() {
        return transactions.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            UserAccount requester = new UserAccount("rabbit-it-" + suffix, "rabbit-it-" + suffix + "@example.test",
                    "unused", "Rabbit integration requester");
            requester.setRoles(Set.of(roles.findByName(RoleName.REQUESTER).orElseThrow()));
            requester = users.save(requester);
            EquipmentCategory category = categories
                    .save(new EquipmentCategory("RIT" + suffix, "Rabbit integration", null));
            Equipment device = equipment.save(new Equipment("RIT-EQ" + suffix, "Rabbit integration", category));
            Instant due = Instant.now().plusSeconds(3600);
            MaintenanceTicket ticket = tickets.save(new MaintenanceTicket("RIT-" + suffix, device, requester,
                    "Rabbit integration", "Real broker delivery", TicketPriority.LOW, due, due));
            TicketEvent event = events.save(new TicketEvent(ticket, null, "ACCEPTED", TicketStatus.SUBMITTED,
                    TicketStatus.ACCEPTED, "Rabbit integration event", "{}"));
            outbox.save(new OutboxEvent(event.getId(), "TICKET", ticket.getId(), "ACCEPTED", "ticket.accepted",
                    "{}"));
            return new Fixture(requester.getId(), category.getId(), device.getId(), ticket.getId(), event.getId());
        });
    }

    private void cleanup(Fixture fixture, UUID unroutableId) {
        transactions.executeWithoutResult(status -> {
            entityManager.createQuery("delete from Notification n where n.eventId = :eventId")
                    .setParameter("eventId", fixture.eventId()).executeUpdate();
            processedMessages.deleteById(fixture.eventId());
            if (outbox.existsById(unroutableId)) {
                outbox.deleteById(unroutableId);
            }
            if (outbox.existsById(fixture.eventId())) {
                outbox.deleteById(fixture.eventId());
            }
            events.deleteById(fixture.eventId());
            tickets.deleteById(fixture.ticketId());
            equipment.deleteById(fixture.equipmentId());
            categories.deleteById(fixture.categoryId());
            users.deleteById(fixture.requesterId());
        });
    }

    private void await(BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000;
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        assertThat(condition.getAsBoolean()).as("condition became true before timeout").isTrue();
    }

    private record Fixture(UUID requesterId, UUID categoryId, UUID equipmentId, UUID ticketId, UUID eventId) {
    }
}
