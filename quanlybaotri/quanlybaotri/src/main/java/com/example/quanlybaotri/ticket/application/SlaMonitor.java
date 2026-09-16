package com.example.quanlybaotri.ticket.application;

import com.example.quanlybaotri.ticket.domain.*;
import com.example.quanlybaotri.ticket.persistence.*;
import java.time.*;
import java.util.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SlaMonitor {
    private static final Set<TicketStatus> TERMINAL = Set.of(TicketStatus.CLOSED, TicketStatus.REJECTED,
            TicketStatus.CANCELLED);
    private final TicketRepository tickets;
    private final TicketEventRepository events;
    private final TicketEventService publisher;

    public SlaMonitor(TicketRepository t, TicketEventRepository e, TicketEventService p) {
        tickets = t;
        events = e;
        publisher = p;
    }

    @Scheduled(fixedDelayString = "${app.sla.scan-ms:60000}")
    @Transactional
    public void scan() {
        Instant now = Instant.now();
        for (MaintenanceTicket t : tickets.findByStatusNotIn(TERMINAL)) {
            if (t.getAcceptedAt() == null)
                check(t, "SLA_RESPONSE", t.getResponseDueAt(), now);
            if (t.getResolvedAt() == null)
                check(t, "SLA_RESOLUTION", t.getResolutionDueAt(), now);
        }
    }

    private void check(MaintenanceTicket t, String prefix, Instant due, Instant now) {
        Instant warning = t.getSubmittedAt()
                .plus(Duration.between(t.getSubmittedAt(), due).multipliedBy(4).dividedBy(5));
        if (!now.isBefore(due)) {
            String type = prefix + "_BREACHED";
            if (!events.existsByTicketIdAndEventType(t.getId(), type))
                publisher.record(t, null, type, t.getStatus(), t.getStatus(), "Phiếu đã quá hạn SLA",
                        Map.of("dueAt", due.toString()));
        } else if (!now.isBefore(warning)) {
            String type = prefix + "_WARNING";
            if (!events.existsByTicketIdAndEventType(t.getId(), type))
                publisher.record(t, null, type, t.getStatus(), t.getStatus(), "Phiếu đã sử dụng 80% thời gian SLA",
                        Map.of("dueAt", due.toString()));
        }
    }
}
