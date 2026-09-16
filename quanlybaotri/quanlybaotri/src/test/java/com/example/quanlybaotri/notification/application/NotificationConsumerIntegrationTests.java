package com.example.quanlybaotri.notification.application;

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
import com.example.quanlybaotri.ticket.api.TicketController.CreateTicketRequest;
import com.example.quanlybaotri.ticket.application.TicketService;
import com.example.quanlybaotri.ticket.domain.TicketPriority;
import com.example.quanlybaotri.ticket.persistence.TicketEventRepository;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationConsumerIntegrationTests {
    @Autowired
    NotificationConsumer consumer;
    @Autowired
    TicketService tickets;
    @Autowired
    TicketEventRepository events;
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

    @Test
    void createsOneNotificationWhenTheSameEventIsDeliveredTwice() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        UserAccount requester = user("consumer-req-" + suffix, RoleName.REQUESTER);
        UserAccount manager = user("consumer-mgr-" + suffix, RoleName.MANAGER);
        EquipmentCategory category = categories
                .save(new EquipmentCategory("CON" + suffix, "Consumer test", null));
        Equipment device = equipment.save(new Equipment("CON-EQ" + suffix, "Consumer test device", category));

        var ticket = tickets.create(new CreateTicketRequest(device.getId(), "RabbitMQ notification test",
                "Verify idempotent event handling", TicketPriority.LOW), requester);
        var event = events.findByTicketIdOrderByCreatedAtAsc(ticket.id()).get(0);

        consumer.consume("{}", event.getId().toString());
        consumer.consume("{}", event.getId().toString());

        assertThat(processedMessages.existsById(event.getId())).isTrue();
        assertThat(notifications.findByUserId(manager.getId(), Pageable.unpaged()).getContent())
                .singleElement()
                .satisfies(notification -> {
                    assertThat(notification.getType()).isEqualTo("SUBMITTED");
                    assertThat(notification.getReferenceId()).isEqualTo(ticket.id());
                });
    }

    private UserAccount user(String username, RoleName roleName) {
        UserAccount user = new UserAccount(username, username + "@example.test", "unused", username);
        user.setRoles(Set.of(roles.findByName(roleName).orElseThrow()));
        return users.save(user);
    }
}
