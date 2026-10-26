package com.example.quanlybaotri.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.example.quanlybaotri.ticket.application.TicketChangeRecorded;
import com.example.quanlybaotri.ticket.domain.TicketStatus;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

class StatisticsReportInvalidationListenerTest {
    @Test
    void invalidatesForStatusAssignmentPriorityCostAndSlaChanges() {
        StatisticsReportCache cache = mock(StatisticsReportCache.class);
        StatisticsReportInvalidationListener listener = new StatisticsReportInvalidationListener(cache);

        listener.onTicketChange(new TicketChangeRecorded("STARTED", TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS));
        listener.onTicketChange(new TicketChangeRecorded("ASSIGNED", TicketStatus.ASSIGNED, TicketStatus.ASSIGNED));
        listener.onTicketChange(new TicketChangeRecorded("PRIORITY_CHANGED", TicketStatus.IN_PROGRESS, TicketStatus.IN_PROGRESS));
        listener.onTicketChange(new TicketChangeRecorded("COST_QUOTED", TicketStatus.RESOLVED, TicketStatus.RESOLVED));
        listener.onTicketChange(new TicketChangeRecorded("SLA_RESOLUTION_WARNING", TicketStatus.IN_PROGRESS, TicketStatus.IN_PROGRESS));

        verify(cache, times(5)).invalidate();
    }

    @Test
    void ignoresEventsThatCannotChangeTheSummary() {
        TicketChangeRecorded attachment = new TicketChangeRecorded(
            "ATTACHMENT_ADDED",
            TicketStatus.IN_PROGRESS,
            TicketStatus.IN_PROGRESS
        );

        assertThat(StatisticsReportInvalidationListener.affectsStatistics(attachment)).isFalse();
    }

    @Test
    void listensOnlyAfterTheSurroundingTransactionCommits() throws Exception {
        TransactionalEventListener annotation = StatisticsReportInvalidationListener.class
            .getMethod("onTicketChange", TicketChangeRecorded.class)
            .getAnnotation(TransactionalEventListener.class);

        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(annotation.fallbackExecution()).isTrue();
    }
}
