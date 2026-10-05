package com.example.quanlybaotri.ticket.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.quanlybaotri.cost.application.CostService;
import com.example.quanlybaotri.cost.persistence.TicketCostSummaryRepository;
import com.example.quanlybaotri.equipment.client.AssetClient;
import com.example.quanlybaotri.identity.client.IdentityClient;
import com.example.quanlybaotri.identity.domain.RoleName;
import com.example.quanlybaotri.organization.client.OrganizationClient;
import com.example.quanlybaotri.ticket.domain.MaintenanceTicket;
import com.example.quanlybaotri.ticket.domain.TicketPriority;
import com.example.quanlybaotri.ticket.domain.TicketStatus;
import com.example.quanlybaotri.ticket.persistence.*;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class TicketServiceTelegramStartTest {

    private UUID ticketId;
    private UUID technicianId;
    private MaintenanceTicket ticket;
    private IdentityClient identities;
    private TicketEventService events;
    private TicketService service;

    @BeforeEach
    void setUp() {
        ticketId = UUID.randomUUID();
        technicianId = UUID.randomUUID();
        ticket = new MaintenanceTicket(
            "BT-2026-000200",
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Lỗi thiết bị",
            "Mô tả",
            TicketPriority.HIGH,
            Instant.now().plusSeconds(60),
            Instant.now().plusSeconds(120)
        );
        ReflectionTestUtils.setField(ticket, "id", ticketId);
        ticket.assign(technicianId);

        TicketRepository tickets = mock(TicketRepository.class);
        when(tickets.findById(ticketId)).thenReturn(Optional.of(ticket));
        identities = mock(IdentityClient.class);
        when(identities.get(technicianId)).thenReturn(technician(technicianId, true));
        events = mock(TicketEventService.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        service = new TicketService(
            tickets,
            mock(SlaPolicyRepository.class),
            events,
            mock(TicketEventRepository.class),
            mock(AssignmentRepository.class),
            mock(WorkLogRepository.class),
            mock(EntityManager.class),
            transactions,
            identities,
            mock(AssetClient.class),
            mock(CostService.class),
            mock(TicketCostSummaryRepository.class),
            mock(OrganizationClient.class)
        );
    }

    @Test
    void startsAssignedTicketAndTreatsRepeatedCallbackAsAlreadyHandled() {
        TicketService.TelegramStartResult first = service.startFromTelegram(ticketId, technicianId);
        TicketService.TelegramStartResult repeated = service.startFromTelegram(
            ticketId,
            technicianId
        );

        assertThat(first.outcome()).isEqualTo(TicketService.TelegramStartOutcome.STARTED);
        assertThat(first.status()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(repeated.outcome()).isEqualTo(
            TicketService.TelegramStartOutcome.ALREADY_HANDLED
        );
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(ticket.getStartedAt()).isNotNull();
        verify(events, times(1)).record(
            eq(ticket),
            eq(technicianId),
            eq("STARTED"),
            eq(TicketStatus.ASSIGNED),
            eq(TicketStatus.IN_PROGRESS),
            contains("Telegram"),
            anyMap()
        );
    }

    @Test
    void refusesTechnicianWhoIsNoLongerAssigned() {
        UUID otherTechnician = UUID.randomUUID();
        when(identities.get(otherTechnician)).thenReturn(technician(otherTechnician, true));

        TicketService.TelegramStartResult result = service.startFromTelegram(
            ticketId,
            otherTechnician
        );

        assertThat(result.outcome()).isEqualTo(
            TicketService.TelegramStartOutcome.NO_LONGER_ASSIGNED
        );
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ASSIGNED);
        verifyNoInteractions(events);
    }

    @Test
    void refusesDisabledTechnician() {
        when(identities.get(technicianId)).thenReturn(technician(technicianId, false));

        TicketService.TelegramStartResult result = service.startFromTelegram(
            ticketId,
            technicianId
        );

        assertThat(result.outcome()).isEqualTo(
            TicketService.TelegramStartOutcome.TECHNICIAN_INELIGIBLE
        );
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ASSIGNED);
        verifyNoInteractions(events);
    }

    private IdentityClient.UserRef technician(UUID id, boolean enabled) {
        return new IdentityClient.UserRef(
            id,
            "tech",
            "Kỹ thuật viên",
            "tech@example.test",
            "INTERNAL",
            null,
            "ALL_SITES",
            Set.of(),
            "ALL_REGIONS",
            Set.of(),
            enabled,
            Set.of(RoleName.TECHNICIAN)
        );
    }
}
