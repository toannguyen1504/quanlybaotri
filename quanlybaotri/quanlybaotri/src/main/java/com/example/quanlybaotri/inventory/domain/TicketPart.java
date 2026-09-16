package com.example.quanlybaotri.inventory.domain;

import com.example.quanlybaotri.identity.domain.UserAccount;
import com.example.quanlybaotri.ticket.domain.MaintenanceTicket;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_parts")
public class TicketPart {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id")
    private MaintenanceTicket ticket;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "part_id")
    private Part part;
    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal quantity;
    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitCost;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "used_by_id")
    private UserAccount usedBy;
    @Column(name = "used_at", nullable = false)
    private Instant usedAt;

    protected TicketPart() {
    }

    public TicketPart(MaintenanceTicket t, Part p, BigDecimal q, BigDecimal cost, UserAccount user) {
        id = UUID.randomUUID();
        ticket = t;
        part = p;
        quantity = q;
        unitCost = cost;
        usedBy = user;
        usedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Part getPart() {
        return part;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public UserAccount getUsedBy() {
        return usedBy;
    }

    public Instant getUsedAt() {
        return usedAt;
    }
}
