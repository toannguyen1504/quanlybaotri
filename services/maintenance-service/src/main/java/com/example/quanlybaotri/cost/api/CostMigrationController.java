package com.example.quanlybaotri.cost.api;

import com.example.quanlybaotri.cost.application.CostService;
import com.example.quanlybaotri.ticket.persistence.TicketRepository;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cost-migration")
public class CostMigrationController {
    private final TicketRepository tickets;
    private final CostService costs;

    public CostMigrationController(TicketRepository tickets, CostService costs) {
        this.tickets=tickets; this.costs=costs;
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

    public record ReconcileResult(int ticketsChecked,int itemsInserted) {}
}
