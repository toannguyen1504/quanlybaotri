package com.example.quanlybaotri.reporting.application;

import com.example.quanlybaotri.ticket.application.TicketChangeRecorded;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class StatisticsReportInvalidationListener {
    private static final Set<String> SAME_STATUS_CHANGES = Set.of(
        "ASSIGNED",
        "PRIORITY_CHANGED",
        "COST_QUOTED",
        "COST_ACCEPTED",
        "COST_DECLINED",
        "SLA_RESPONSE_WARNING",
        "SLA_RESPONSE_BREACHED",
        "SLA_RESOLUTION_WARNING",
        "SLA_RESOLUTION_BREACHED"
    );

    private final StatisticsReportCache cache;

    public StatisticsReportInvalidationListener(StatisticsReportCache cache) {
        this.cache = cache;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTicketChange(TicketChangeRecorded change) {
        if (affectsStatistics(change)) cache.invalidate();
    }

    static boolean affectsStatistics(TicketChangeRecorded change) {
        return !Objects.equals(change.from(), change.to()) || SAME_STATUS_CHANGES.contains(change.action());
    }
}
