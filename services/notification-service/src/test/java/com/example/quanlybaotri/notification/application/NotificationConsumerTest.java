package com.example.quanlybaotri.notification.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.quanlybaotri.identity.client.IdentityClient;
import com.example.quanlybaotri.notification.domain.Notification;
import com.example.quanlybaotri.notification.persistence.NotificationRepository;
import com.example.quanlybaotri.notification.persistence.ProcessedMessageRepository;
import com.example.quanlybaotri.telegram.domain.TelegramDelivery;
import com.example.quanlybaotri.telegram.domain.TelegramLink;
import com.example.quanlybaotri.telegram.persistence.TelegramDeliveryRepository;
import com.example.quanlybaotri.telegram.persistence.TelegramLinkRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

class NotificationConsumerTest {

    private ProcessedMessageRepository processed;
    private NotificationRepository notifications;
    private TelegramLinkRepository links;
    private TelegramDeliveryRepository deliveries;
    private NotificationConsumer consumer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        processed = mock(ProcessedMessageRepository.class);
        notifications = mock(NotificationRepository.class);
        links = mock(TelegramLinkRepository.class);
        deliveries = mock(TelegramDeliveryRepository.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(mock(TransactionStatus.class));
            return null;
        })
            .when(transactions)
            .executeWithoutResult(any());
        when(notifications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        consumer = new NotificationConsumer(
            processed,
            notifications,
            mock(IdentityClient.class),
            new ObjectMapper(),
            transactions,
            links,
            deliveries
        );
    }

    @Test
    void enqueuesTelegramDeliveryForLinkedAssignee() throws Exception {
        UUID actor = UUID.randomUUID();
        UUID requester = UUID.randomUUID();
        UUID assignee = UUID.randomUUID();
        when(links.findByUserIdAndActiveTrue(assignee)).thenReturn(
            Optional.of(new TelegramLink(assignee, 123L, 123L, "tech"))
        );

        consumer.consume(ticketEvent(actor, requester, assignee));

        verify(notifications, times(2)).save(any(Notification.class));
        verify(deliveries).save(any(TelegramDelivery.class));
    }

    @Test
    void doesNotNotifyTechnicianAboutTheirOwnAction() throws Exception {
        UUID requester = UUID.randomUUID();
        UUID assignee = UUID.randomUUID();

        consumer.consume(ticketEvent(assignee, requester, assignee));

        verify(links, never()).findByUserIdAndActiveTrue(assignee);
        verify(deliveries, never()).save(any());
    }

    private String ticketEvent(UUID actor, UUID requester, UUID assignee) {
        return """
            {
              "eventId":"%s",
              "eventType":"maintenance.ticket.changed",
              "schemaVersion":2,
              "data":{
                "ticketId":"%s",
                "ticketCode":"BT-001",
                "action":"ASSIGNED",
                "status":"ASSIGNED",
                "priority":"HIGH",
                "actorId":"%s",
                "requesterId":"%s",
                "assigneeId":"%s",
                "description":"Đã phân công"
              }
            }
            """.formatted(UUID.randomUUID(), UUID.randomUUID(), actor, requester, assignee);
    }
}
