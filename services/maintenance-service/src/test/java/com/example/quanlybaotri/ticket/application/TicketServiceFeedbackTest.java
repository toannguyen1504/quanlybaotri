package com.example.quanlybaotri.ticket.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.quanlybaotri.cost.application.CostService;
import com.example.quanlybaotri.cost.persistence.TicketCostSummaryRepository;
import com.example.quanlybaotri.equipment.client.AssetClient;
import com.example.quanlybaotri.identity.client.IdentityClient;
import com.example.quanlybaotri.identity.domain.RoleName;
import com.example.quanlybaotri.organization.client.OrganizationClient;
import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.shared.security.CurrentUser.Actor;
import com.example.quanlybaotri.ticket.api.TicketController.FeedbackRequest;
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
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class TicketServiceFeedbackTest {

    @Test
    void requesterClosesWithTrimmedFeedbackAndSafeEventMetadata() {
        UUID requesterId = UUID.randomUUID();
        Fixture fixture = fixture(requesterId);
        Actor requester = actor(requesterId, RoleName.REQUESTER, "CUSTOMER");

        var result = fixture.service.close(
            fixture.ticket.getId(),
            null,
            new FeedbackRequest(5, "  Hỗ trợ rất tốt  "),
            requester
        );

        assertThat(fixture.ticket.getStatus()).isEqualTo(TicketStatus.CLOSED);
        assertThat(fixture.ticket.getFeedbackRating()).isEqualTo(5);
        assertThat(fixture.ticket.getFeedbackComment()).isEqualTo("Hỗ trợ rất tốt");
        assertThat(result.feedback()).isNotNull();
        assertThat(result.feedback().rating()).isEqualTo(5);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(fixture.events).record(
            eq(fixture.ticket),
            eq(requesterId),
            eq("CLOSED"),
            eq(TicketStatus.RESOLVED),
            eq(TicketStatus.CLOSED),
            anyString(),
            metadata.capture()
        );
        assertThat(metadata.getValue())
            .containsEntry("feedbackProvided", true)
            .containsEntry("feedbackRating", 5)
            .doesNotContainKey("feedbackComment");
    }

    @Test
    void requesterCanSkipFeedbackWhenClosing() {
        UUID requesterId = UUID.randomUUID();
        Fixture fixture = fixture(requesterId);

        var result = fixture.service.close(
            fixture.ticket.getId(),
            null,
            null,
            actor(requesterId, RoleName.REQUESTER, "CUSTOMER")
        );

        assertThat(result.status()).isEqualTo(TicketStatus.CLOSED);
        assertThat(result.feedback()).isNull();
        assertThat(fixture.ticket.getFeedbackSubmittedAt()).isNull();
    }

    @Test
    void managerCannotSubmitFeedbackForRequesterAndStillNeedsAReason() {
        UUID requesterId = UUID.randomUUID();
        Fixture missingReason = fixture(requesterId);
        Actor manager = actor(UUID.randomUUID(), RoleName.MANAGER, "INTERNAL");

        assertThatThrownBy(() -> missingReason.service.close(
            missingReason.ticket.getId(),
            null,
            null,
            manager
        )).isInstanceOfSatisfying(ApiException.class, error ->
            assertThat(error.getCode()).isEqualTo("REASON_REQUIRED")
        );

        Fixture impersonation = fixture(requesterId);
        assertThatThrownBy(() -> impersonation.service.close(
            impersonation.ticket.getId(),
            "Đóng thay người yêu cầu",
            new FeedbackRequest(4, null),
            manager
        )).isInstanceOfSatisfying(ApiException.class, error ->
            assertThat(error.getStatus().value()).isEqualTo(403)
        );
        assertThat(impersonation.ticket.getStatus()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(impersonation.ticket.getFeedbackSubmittedAt()).isNull();
    }

    private Fixture fixture(UUID requesterId) {
        UUID ticketId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        MaintenanceTicket ticket = new MaintenanceTicket(
            "BT-2026-000200",
            UUID.randomUUID(),
            requesterId,
            UUID.randomUUID(),
            siteId,
            "Lỗi thiết bị",
            "Mô tả",
            TicketPriority.MEDIUM,
            Instant.now().plusSeconds(60),
            Instant.now().plusSeconds(120)
        );
        ReflectionTestUtils.setField(ticket, "id", ticketId);
        ticket.resolve("Đã xử lý xong");

        TicketRepository tickets = mock(TicketRepository.class);
        when(tickets.findById(ticketId)).thenReturn(Optional.of(ticket));
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        IdentityClient identities = mock(IdentityClient.class);
        when(identities.resolve(any())).thenReturn(Map.of());
        OrganizationClient organizations = mock(OrganizationClient.class);
        when(organizations.resolve(any())).thenReturn(Map.of());
        CostService costs = mock(CostService.class);
        when(costs.paymentSnapshots(any())).thenReturn(Map.of());
        TicketCostSummaryRepository costSummaries = mock(TicketCostSummaryRepository.class);
        when(costSummaries.findById(ticketId)).thenReturn(Optional.empty());
        TicketEventService events = mock(TicketEventService.class);

        TicketService service = new TicketService(
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
            costs,
            costSummaries,
            organizations
        );
        return new Fixture(service, ticket, events);
    }

    private Actor actor(UUID id, RoleName role, String accountType) {
        return new Actor(
            id,
            role.name().toLowerCase(),
            role.name(),
            Set.of(role),
            "token",
            accountType,
            null,
            "ALL_SITES",
            Set.of()
        );
    }

    private record Fixture(
        TicketService service,
        MaintenanceTicket ticket,
        TicketEventService events
    ) {}
}
