package com.example.quanlybaotri.ticket.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.quanlybaotri.cost.application.CostService;
import com.example.quanlybaotri.cost.persistence.TicketCostSummaryRepository;
import com.example.quanlybaotri.equipment.client.AssetClient;
import com.example.quanlybaotri.identity.client.IdentityClient;
import com.example.quanlybaotri.identity.domain.RoleName;
import com.example.quanlybaotri.organization.client.OrganizationClient;
import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.shared.security.CurrentUser.Actor;
import com.example.quanlybaotri.ticket.domain.MaintenanceTicket;
import com.example.quanlybaotri.ticket.domain.TicketPriority;
import com.example.quanlybaotri.ticket.domain.TicketStatus;
import com.example.quanlybaotri.ticket.persistence.*;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.test.util.ReflectionTestUtils;

class TicketServiceRegionAssignmentTest {

    @Test
    void rejectsOutsideRegionAssignmentUntilItIsConfirmed() {
        UUID siteId = UUID.randomUUID();
        UUID ticketRegion = UUID.randomUUID();
        UUID technicianRegion = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();
        MaintenanceTicket ticket = new MaintenanceTicket(
            "BT-2026-000100", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), siteId,
            "Lỗi thiết bị", "Mô tả", TicketPriority.MEDIUM,
            Instant.now().plusSeconds(60), Instant.now().plusSeconds(120)
        );
        ticket.accept();

        IdentityClient identities = mock(IdentityClient.class);
        when(identities.get(technicianId)).thenReturn(new IdentityClient.UserRef(
            technicianId, "tech", "Kỹ thuật viên", "tech@example.test", "INTERNAL",
            null, "ALL_SITES", Set.of(), "SELECTED_REGIONS", Set.of(technicianRegion), true,
            Set.of(RoleName.TECHNICIAN)
        ));
        OrganizationClient organizations = mock(OrganizationClient.class);
        when(organizations.resolve(any())).thenReturn(Map.of(siteId, new OrganizationClient.DepartmentRef(
            siteId, "SITE", "Địa điểm", ticket.getCustomerId(), "Khách hàng",
            ticketRegion, "Miền Bắc", true, true
        )));
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any())).thenReturn(ticket);

        TicketService service = new TicketService(
            mock(TicketRepository.class), mock(SlaPolicyRepository.class), mock(TicketEventService.class),
            mock(TicketEventRepository.class), mock(AssignmentRepository.class), mock(WorkLogRepository.class),
            mock(EntityManager.class), transactions, identities, mock(AssetClient.class), mock(CostService.class),
            mock(TicketCostSummaryRepository.class), organizations
        );
        Actor manager = new Actor(
            UUID.randomUUID(), "manager", "Quản lý", Set.of(RoleName.MANAGER), "token",
            "INTERNAL", null, "ALL_SITES", Set.of()
        );

        assertThatThrownBy(() -> service.assign(ticket.getId(), technicianId, "Phân công", false, manager))
            .isInstanceOfSatisfying(ApiException.class, error -> {
                org.assertj.core.api.Assertions.assertThat(error.getCode()).isEqualTo("OUTSIDE_TECHNICIAN_REGION");
                org.assertj.core.api.Assertions.assertThat(error.getStatus().value()).isEqualTo(409);
            });
    }

    @Test
    void assignsOutsideRegionAfterConfirmation() {
        UUID ticketId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        UUID ticketRegion = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();
        MaintenanceTicket ticket = new MaintenanceTicket(
            "BT-2026-000101", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), siteId,
            "Lỗi thiết bị", "Mô tả", TicketPriority.MEDIUM,
            Instant.now().plusSeconds(60), Instant.now().plusSeconds(120)
        );
        ReflectionTestUtils.setField(ticket, "id", ticketId);
        ticket.accept();

        TicketRepository tickets = mock(TicketRepository.class);
        when(tickets.findById(ticketId)).thenReturn(Optional.of(ticket));
        IdentityClient identities = mock(IdentityClient.class);
        when(identities.get(technicianId)).thenReturn(new IdentityClient.UserRef(
            technicianId, "tech", "Kỹ thuật viên", "tech@example.test", "INTERNAL",
            null, "ALL_SITES", Set.of(), "UNASSIGNED", Set.of(), true,
            Set.of(RoleName.TECHNICIAN)
        ));
        when(identities.resolve(any())).thenReturn(Map.of());
        OrganizationClient organizations = mock(OrganizationClient.class);
        when(organizations.resolve(any())).thenReturn(Map.of(siteId, new OrganizationClient.DepartmentRef(
            siteId, "SITE", "Địa điểm", ticket.getCustomerId(), "Khách hàng",
            ticketRegion, "Miền Bắc", true, true
        )));
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        AssignmentRepository assignments = mock(AssignmentRepository.class);
        when(assignments.findFirstByTicketIdAndUnassignedAtIsNullOrderByAssignedAtDesc(ticketId))
            .thenReturn(Optional.empty());
        TicketCostSummaryRepository costSummaries = mock(TicketCostSummaryRepository.class);
        when(costSummaries.findById(ticketId)).thenReturn(Optional.empty());

        TicketService service = new TicketService(
            tickets, mock(SlaPolicyRepository.class), mock(TicketEventService.class),
            mock(TicketEventRepository.class), assignments, mock(WorkLogRepository.class),
            mock(EntityManager.class), transactions, identities, mock(AssetClient.class), mock(CostService.class),
            costSummaries, organizations
        );
        Actor manager = new Actor(
            UUID.randomUUID(), "manager", "Quản lý", Set.of(RoleName.MANAGER), "token",
            "INTERNAL", null, "ALL_SITES", Set.of()
        );

        service.assign(ticketId, technicianId, "Ngoài vùng đã xác nhận", true, manager);

        org.assertj.core.api.Assertions.assertThat(ticket.getAssigneeId()).isEqualTo(technicianId);
        org.assertj.core.api.Assertions.assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ASSIGNED);
    }
}
