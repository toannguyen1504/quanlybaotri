package com.example.quanlybaotri.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.quanlybaotri.cost.domain.CostStatus;
import com.example.quanlybaotri.ticket.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StatisticsReportServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");

    @Test
    void dueSoonStartsAtEightyPercentAndDoesNotOverlapOverdue() {
        var atWarning = ticket(TicketStatus.IN_PROGRESS,
            Instant.parse("2026-10-01T08:20:00Z"), Instant.parse("2026-10-01T10:25:00Z"), null);
        var beforeWarning = ticket(TicketStatus.IN_PROGRESS,
            Instant.parse("2026-10-01T08:21:00Z"), Instant.parse("2026-10-01T10:25:00Z"), null);
        var overdue = ticket(TicketStatus.IN_PROGRESS,
            Instant.parse("2026-10-01T08:00:00Z"), Instant.parse("2026-10-01T10:00:00Z"), null);

        assertThat(StatisticsReportService.matchingQueues(atWarning, NOW))
            .contains(TicketWorkQueue.RESOLUTION_DUE_SOON)
            .doesNotContain(TicketWorkQueue.RESOLUTION_OVERDUE);
        assertThat(StatisticsReportService.matchingQueues(beforeWarning, NOW))
            .doesNotContain(TicketWorkQueue.RESOLUTION_DUE_SOON);
        assertThat(StatisticsReportService.matchingQueues(overdue, NOW))
            .contains(TicketWorkQueue.RESOLUTION_OVERDUE)
            .doesNotContain(TicketWorkQueue.RESOLUTION_DUE_SOON);
    }

    @Test
    void responseAndWorkflowQueuesFollowCurrentStatus() {
        var submitted = ticket(TicketStatus.SUBMITTED,
            Instant.parse("2026-10-01T08:00:00Z"), Instant.parse("2026-10-01T12:00:00Z"), null);

        assertThat(StatisticsReportService.matchingQueues(submitted, NOW))
            .contains(TicketWorkQueue.AWAITING_ACCEPTANCE, TicketWorkQueue.RESPONSE_OVERDUE);
        assertThat(StatisticsReportService.matchingQueues(
            ticket(TicketStatus.ACCEPTED, NOW, NOW.plusSeconds(3600), null), NOW))
            .contains(TicketWorkQueue.AWAITING_ASSIGNMENT);
        assertThat(StatisticsReportService.matchingQueues(
            ticket(TicketStatus.RESOLVED, NOW, NOW.plusSeconds(3600), CostStatus.ACCEPTED), NOW))
            .containsExactly(TicketWorkQueue.AWAITING_CLOSURE);
    }

    @Test
    void resolvedTicketUsesCostWorkflowQueuesAndTreatsMissingSummaryAsDraft() {
        assertThat(StatisticsReportService.matchingQueues(
            ticket(TicketStatus.RESOLVED, NOW, NOW.plusSeconds(3600), null), NOW))
            .contains(TicketWorkQueue.AWAITING_CLOSURE, TicketWorkQueue.COST_DRAFT);
        assertThat(StatisticsReportService.matchingQueues(
            ticket(TicketStatus.RESOLVED, NOW, NOW.plusSeconds(3600), CostStatus.QUOTED), NOW))
            .contains(TicketWorkQueue.AWAITING_CLOSURE, TicketWorkQueue.COST_QUOTED)
            .doesNotContain(TicketWorkQueue.COST_DRAFT);
    }

    private StatisticsReportService.RawTicket ticket(
        TicketStatus status,
        Instant submittedAt,
        Instant resolutionDueAt,
        CostStatus costStatus
    ) {
        return new StatisticsReportService.RawTicket(
            UUID.randomUUID(), "BT-001", "Kiểm tra thiết bị", UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID(), status, TicketPriority.MEDIUM, submittedAt,
            NOW.minusSeconds(1), resolutionDueAt, costStatus, BigDecimal.ZERO
        );
    }
}
