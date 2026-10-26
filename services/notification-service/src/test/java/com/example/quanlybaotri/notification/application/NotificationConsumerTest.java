package com.example.quanlybaotri.notification.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

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
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

class NotificationConsumerTest {

    private ProcessedMessageRepository processed;
    private NotificationRepository notifications;
    private TelegramLinkRepository links;
    private TelegramDeliveryRepository deliveries;
    private IdentityClient identities;
    private NotificationConsumer consumer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        processed = mock(ProcessedMessageRepository.class);
        notifications = mock(NotificationRepository.class);
        links = mock(TelegramLinkRepository.class);
        deliveries = mock(TelegramDeliveryRepository.class);
        identities = mock(IdentityClient.class);
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
            identities,
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
        ArgumentCaptor<TelegramDelivery> delivery = ArgumentCaptor.forClass(
            TelegramDelivery.class
        );
        verify(deliveries).save(delivery.capture());
        assertThat(delivery.getValue().getUserId()).isEqualTo(assignee);
        assertThat(delivery.getValue().getAction()).isEqualTo(
            TelegramDelivery.Action.START_ASSIGNED_TICKET
        );
    }

    @Test
    void doesNotNotifyTechnicianAboutTheirOwnAction() throws Exception {
        UUID requester = UUID.randomUUID();
        UUID assignee = UUID.randomUUID();

        consumer.consume(ticketEvent(assignee, requester, assignee));

        verify(links, never()).findByUserIdAndActiveTrue(assignee);
        verify(deliveries, never()).save(any());
    }

    @Test
    void onlyAssigneeDeliveryReceivesTelegramAction() throws Exception {
        UUID actor = UUID.randomUUID();
        UUID requester = UUID.randomUUID();
        UUID assignee = UUID.randomUUID();
        when(links.findByUserIdAndActiveTrue(requester)).thenReturn(
            Optional.of(new TelegramLink(requester, 111L, 111L, "requester"))
        );
        when(links.findByUserIdAndActiveTrue(assignee)).thenReturn(
            Optional.of(new TelegramLink(assignee, 222L, 222L, "assignee"))
        );

        consumer.consume(ticketEvent(actor, requester, assignee));

        ArgumentCaptor<TelegramDelivery> captured = ArgumentCaptor.forClass(
            TelegramDelivery.class
        );
        verify(deliveries, times(2)).save(captured.capture());
        TelegramDelivery requesterDelivery = captured
            .getAllValues()
            .stream()
            .filter(delivery -> delivery.getUserId().equals(requester))
            .findFirst()
            .orElseThrow();
        TelegramDelivery assigneeDelivery = captured
            .getAllValues()
            .stream()
            .filter(delivery -> delivery.getUserId().equals(assignee))
            .findFirst()
            .orElseThrow();
        assertThat(requesterDelivery.getAction()).isNull();
        assertThat(assigneeDelivery.getAction()).isEqualTo(
            TelegramDelivery.Action.START_ASSIGNED_TICKET
        );
    }

    @Test
    void quoteEventsNotifyTheOtherPartyWithoutBroadcastingToManagers() throws Exception {
        UUID requester = UUID.randomUUID();
        UUID assignee = UUID.randomUUID();

        consumer.consume(costEvent("COST_QUOTED", assignee, requester, assignee));
        consumer.consume(costEvent("COST_ACCEPTED", requester, requester, assignee));

        ArgumentCaptor<Notification> captured = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, times(2)).save(captured.capture());
        assertThat(captured.getAllValues()).extracting(Notification::getUserId)
            .containsExactlyInAnyOrder(requester, assignee);
        verify(identities, never()).enabledByRoles(any());
    }

    @Test
    void notifiesManagersWithoutLeakingFeedbackContent() throws Exception {
        UUID admin = UUID.randomUUID();
        UUID manager = UUID.randomUUID();
        UUID requester = UUID.randomUUID();
        UUID feedbackId = UUID.randomUUID();
        when(identities.enabledByRoles(java.util.Set.of("ADMIN", "MANAGER")))
            .thenReturn(java.util.Set.of(admin, manager));

        consumer.consume(systemFeedbackEvent(
            "CREATED", requester, requester, feedbackId, "SUGGESTION", "NEW"
        ));

        ArgumentCaptor<Notification> captured = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, times(2)).save(captured.capture());
        assertThat(captured.getAllValues()).allSatisfy(notification -> {
            assertThat(notification.getType()).isEqualTo("SYSTEM_FEEDBACK_NEW");
            assertThat(notification.getReferenceType()).isEqualTo("SYSTEM_FEEDBACK");
            assertThat(notification.getReferenceId()).isEqualTo(feedbackId);
            assertThat(notification.getMessage()).doesNotContain("Nội dung bí mật");
        });
    }

    @Test
    void notifiesRequesterWhenFeedbackStatusChanges() throws Exception {
        UUID requester = UUID.randomUUID();
        UUID manager = UUID.randomUUID();
        UUID feedbackId = UUID.randomUUID();

        consumer.consume(systemFeedbackEvent(
            "STATUS_CHANGED", requester, manager, feedbackId, "BUG_REPORT", "RESOLVED"
        ));

        ArgumentCaptor<Notification> captured = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(captured.capture());
        assertThat(captured.getValue().getUserId()).isEqualTo(requester);
        assertThat(captured.getValue().getType()).isEqualTo("SYSTEM_FEEDBACK_UPDATED");
        assertThat(captured.getValue().getReferenceId()).isEqualTo(feedbackId);
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

    private String systemFeedbackEvent(
        String action,
        UUID requester,
        UUID actor,
        UUID feedbackId,
        String category,
        String status
    ) {
        return """
            {
              "eventId":"%s",
              "eventType":"maintenance.system-feedback.changed",
              "schemaVersion":2,
              "data":{
                "feedbackId":"%s",
                "action":"%s",
                "requesterId":"%s",
                "actorId":"%s",
                "category":"%s",
                "status":"%s"
              }
            }
            """.formatted(UUID.randomUUID(), feedbackId, action, requester, actor, category, status);
    }

    private String costEvent(String action, UUID actor, UUID requester, UUID assignee) {
        return """
            {
              "eventId":"%s",
              "eventType":"maintenance.ticket.changed",
              "schemaVersion":2,
              "data":{
                "ticketId":"%s",
                "ticketCode":"BT-001",
                "action":"%s",
                "status":"RESOLVED",
                "priority":"MEDIUM",
                "actorId":"%s",
                "requesterId":"%s",
                "assigneeId":"%s",
                "description":"Cập nhật báo giá"
              }
            }
            """.formatted(UUID.randomUUID(), UUID.randomUUID(), action, actor, requester, assignee);
    }
}
