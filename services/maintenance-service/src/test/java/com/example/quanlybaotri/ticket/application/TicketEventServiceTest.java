package com.example.quanlybaotri.ticket.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.quanlybaotri.shared.messaging.OutboxRepository;
import com.example.quanlybaotri.ticket.domain.*;
import com.example.quanlybaotri.ticket.persistence.TicketEventRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class TicketEventServiceTest {
    @Test
    void publishesAnInternalChangeEventAlongsideThePersistentEvent() {
        TicketEventRepository events = mock(TicketEventRepository.class);
        when(events.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        OutboxRepository outbox = mock(OutboxRepository.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        TicketEventService service = new TicketEventService(events, outbox, new ObjectMapper(), publisher);
        MaintenanceTicket ticket = new MaintenanceTicket(
            "BT-2026-000001", UUID.randomUUID(), UUID.randomUUID(), "Kiểm tra thiết bị", "Mô tả",
            TicketPriority.HIGH, Instant.now().plusSeconds(60), Instant.now().plusSeconds(3600)
        );
        ReflectionTestUtils.setField(ticket, "id", UUID.randomUUID());

        service.record(
            ticket,
            UUID.randomUUID(),
            "ACCEPTED",
            TicketStatus.SUBMITTED,
            TicketStatus.ACCEPTED,
            "Đã tiếp nhận",
            Map.of()
        );

        ArgumentCaptor<Object> change = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishEvent(change.capture());
        assertThat(change.getValue()).isEqualTo(
            new TicketChangeRecorded("ACCEPTED", TicketStatus.SUBMITTED, TicketStatus.ACCEPTED)
        );
        verify(outbox).save(any());
    }
}
