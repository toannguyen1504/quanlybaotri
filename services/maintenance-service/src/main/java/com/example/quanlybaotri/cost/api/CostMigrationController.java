package com.example.quanlybaotri.cost.api;

import com.example.quanlybaotri.cost.application.CostService;
import com.example.quanlybaotri.cost.domain.CostStatus;
import com.example.quanlybaotri.cost.persistence.TicketCostSummaryRepository;
import com.example.quanlybaotri.shared.security.CurrentUser;
import com.example.quanlybaotri.ticket.persistence.TicketRepository;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cost-migration")
public class CostMigrationController {
    private final TicketRepository tickets;
    private final TicketCostSummaryRepository summaries;
    private final CostService costs;
    private final CurrentUser current;

    public CostMigrationController(TicketRepository tickets, TicketCostSummaryRepository summaries, CostService costs, CurrentUser current) {
        this.tickets=tickets; this.summaries=summaries; this.costs=costs; this.current=current;
    }

    @PostMapping("/reconcile")
    @PreAuthorize("hasRole('ADMIN')")
    public ReconcileResult reconcile() {
        int ticketsChecked=0, itemsInserted=0;
        for(UUID ticketId:tickets.findAll().stream().map(ticket -> ticket.getId()).toList()) {
            ticketsChecked++; itemsInserted+=costs.reconcileForMigration(ticketId);
        }
        return new ReconcileResult(ticketsChecked,itemsInserted);
    }

    @PostMapping("/bulk-approve-ready")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public BulkApprovalResult bulkApproveReady() {
        var actor=current.require(); List<UUID> approved=new ArrayList<>(), skipped=new ArrayList<>();
        for(var summary:summaries.findByStatus(CostStatus.READY)) {
            try { costs.approve(summary.getTicketId(),actor); approved.add(summary.getTicketId()); }
            catch (com.example.quanlybaotri.shared.api.ApiException exception) { skipped.add(summary.getTicketId()); }
        }
        return new BulkApprovalResult(approved.size(),approved,skipped.size(),skipped);
    }

    public record ReconcileResult(int ticketsChecked,int itemsInserted) {}
    public record BulkApprovalResult(int approvedCount,List<UUID> ticketIds,int skippedCount,List<UUID> skippedTicketIds) {}
}
