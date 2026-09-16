package com.example.quanlybaotri.inventory.domain;

import com.example.quanlybaotri.identity.domain.UserAccount;
import com.example.quanlybaotri.ticket.domain.MaintenanceTicket;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_movements")
public class StockMovement {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "part_id")
    private Part part;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    private MaintenanceTicket ticket;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id")
    private UserAccount actor;
    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 20)
    private StockMovementType type;
    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal quantity;
    @Column(name = "balance_after", nullable = false, precision = 19, scale = 3)
    private BigDecimal balanceAfter;
    @Column(name = "unit_cost", precision = 19, scale = 2)
    private BigDecimal unitCost;
    @Column(nullable = false, length = 500)
    private String reason;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StockMovement() {
    }

    public StockMovement(Part p, MaintenanceTicket t, UserAccount a, StockMovementType type, BigDecimal quantity,
            BigDecimal balance, BigDecimal cost, String reason) {
        id = UUID.randomUUID();
        part = p;
        ticket = t;
        actor = a;
        this.type = type;
        this.quantity = quantity;
        balanceAfter = balance;
        unitCost = cost;
        this.reason = reason;
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Part getPart() {
        return part;
    }

    public MaintenanceTicket getTicket() {
        return ticket;
    }

    public UserAccount getActor() {
        return actor;
    }

    public StockMovementType getType() {
        return type;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
