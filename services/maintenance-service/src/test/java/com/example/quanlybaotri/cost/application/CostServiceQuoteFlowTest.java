package com.example.quanlybaotri.cost.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.quanlybaotri.cost.domain.*;
import com.example.quanlybaotri.cost.persistence.*;
import com.example.quanlybaotri.identity.domain.RoleName;
import com.example.quanlybaotri.payment.client.PaymentClient;
import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.shared.security.CurrentUser.Actor;
import com.example.quanlybaotri.ticket.application.TicketEventService;
import com.example.quanlybaotri.ticket.domain.*;
import com.example.quanlybaotri.ticket.persistence.TicketRepository;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CostServiceQuoteFlowTest {
    private final UUID ticketId = UUID.randomUUID();
    private final UUID technicianId = UUID.randomUUID();
    private final UUID requesterId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID siteId = UUID.randomUUID();
    private final TicketRepository tickets = mock(TicketRepository.class);
    private final TicketCostSummaryRepository summaries = mock(TicketCostSummaryRepository.class);
    private final TicketCostItemRepository items = mock(TicketCostItemRepository.class);
    private final TicketEventService events = mock(TicketEventService.class);
    private final InventoryCostClient inventory = mock(InventoryCostClient.class);
    private final PaymentClient payments = mock(PaymentClient.class);
    private final MaintenanceTicket ticket = mock(MaintenanceTicket.class);
    private TicketCostSummary summary;
    private CostService service;

    @BeforeEach
    void setUp() {
        summary = new TicketCostSummary(ticketId);
        when(tickets.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticket.getId()).thenReturn(ticketId);
        when(ticket.getStatus()).thenReturn(TicketStatus.RESOLVED);
        when(ticket.getAssigneeId()).thenReturn(technicianId);
        when(ticket.getRequesterId()).thenReturn(requesterId);
        when(ticket.getCustomerId()).thenReturn(customerId);
        when(ticket.getSiteId()).thenReturn(siteId);
        when(summaries.findById(ticketId)).thenReturn(Optional.of(summary));
        when(items.findByTicketIdOrderByIncurredAtAscCreatedAtAsc(ticketId)).thenReturn(List.of());
        when(inventory.parts(ticketId)).thenReturn(List.of());
        service = new CostService(tickets, summaries, items, events, inventory, payments);
    }

    @Test
    void assignedTechnicianQuotesAndRequesterAccepts() {
        var quoted = service.quote(ticketId, technician());
        assertThat(quoted.status()).isEqualTo(CostStatus.QUOTED);

        var accepted = service.accept(ticketId, requester());
        assertThat(accepted.status()).isEqualTo(CostStatus.ACCEPTED);
        assertThat(summary.getAcceptedById()).isEqualTo(requesterId);
        verify(events).record(eq(ticket), eq(technicianId), eq("COST_QUOTED"),
            eq(TicketStatus.RESOLVED), eq(TicketStatus.RESOLVED), anyString(), anyMap());
        verify(events).record(eq(ticket), eq(requesterId), eq("COST_ACCEPTED"),
            eq(TicketStatus.RESOLVED), eq(TicketStatus.RESOLVED), anyString(), anyMap());
    }

    @Test
    void onlyRequesterCanDecideAndDeclineReturnsQuoteToDraft() {
        service.quote(ticketId, technician());
        assertThatThrownBy(() -> service.accept(ticketId, manager()))
            .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.decline(ticketId, "  ", requester()))
            .isInstanceOf(ApiException.class);

        var declined = service.decline(ticketId, "Chi phí phát sinh chưa rõ", requester());
        assertThat(declined.status()).isEqualTo(CostStatus.DRAFT);
        assertThat(summary.getDeclineReason()).isEqualTo("Chi phí phát sinh chưa rõ");
    }

    private Actor technician() {
        return new Actor(technicianId, "tech", "Kỹ thuật viên", Set.of(RoleName.TECHNICIAN),
            "token", "INTERNAL", null, null, Set.of());
    }

    private Actor requester() {
        return new Actor(requesterId, "customer", "Khách hàng", Set.of(RoleName.REQUESTER),
            "token", "CUSTOMER", customerId, "SELECTED_SITES", Set.of(siteId));
    }

    private Actor manager() {
        return new Actor(UUID.randomUUID(), "manager", "Quản lý", Set.of(RoleName.MANAGER),
            "token", "INTERNAL", null, null, Set.of());
    }
}
