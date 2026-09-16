package com.example.quanlybaotri.notification.application;

import com.example.quanlybaotri.identity.domain.*;
import com.example.quanlybaotri.identity.persistence.UserRepository;
import com.example.quanlybaotri.notification.domain.*;
import com.example.quanlybaotri.notification.persistence.*;
import com.example.quanlybaotri.shared.messaging.RabbitConfig;
import com.example.quanlybaotri.ticket.domain.*;
import com.example.quanlybaotri.ticket.persistence.TicketEventRepository;
import java.util.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NotificationConsumer {
    private final ProcessedMessageRepository processed;
    private final TicketEventRepository events;
    private final UserRepository users;
    private final NotificationRepository notifications;

    public NotificationConsumer(ProcessedMessageRepository p, TicketEventRepository e, UserRepository u,
            NotificationRepository n) {
        processed = p;
        events = e;
        users = u;
        notifications = n;
    }

    @RabbitListener(queues = RabbitConfig.NOTIFICATION_QUEUE)
    @Transactional
    public void consume(String ignored, @Header("eventId") String eventId) {
        UUID id = UUID.fromString(eventId);
        if (processed.existsById(id))
            return;
        TicketEvent event = events.findById(id).orElseThrow();
        MaintenanceTicket ticket = event.getTicket();
        Set<UserAccount> recipients = new LinkedHashSet<>();
        if (event.getEventType().equals("SUBMITTED") || event.getEventType().equals("PART_LOW_STOCK"))
            recipients.addAll(users.findEnabledByRoleNames(Set.of(RoleName.ADMIN, RoleName.MANAGER)));
        else {
            recipients.add(ticket.getRequester());
            if (ticket.getAssignee() != null)
                recipients.add(ticket.getAssignee());
        }
        if (event.getActor() != null)
            recipients.removeIf(u -> u.getId().equals(event.getActor().getId()));
        for (UserAccount u : recipients)
            notifications.save(new Notification(u, id, event.getEventType(), "Phiếu " + ticket.getCode(),
                    event.getDescription(), "TICKET", ticket.getId()));
        processed.save(new ProcessedMessage(id));
    }
}
