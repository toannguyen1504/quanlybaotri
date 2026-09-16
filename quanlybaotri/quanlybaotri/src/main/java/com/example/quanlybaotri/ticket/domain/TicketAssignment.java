package com.example.quanlybaotri.ticket.domain;

import com.example.quanlybaotri.identity.domain.UserAccount;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_assignment_history")
public class TicketAssignment {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id")
    private MaintenanceTicket ticket;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "technician_id")
    private UserAccount technician;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by_id")
    private UserAccount assignedBy;
    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;
    @Column(name = "unassigned_at")
    private Instant unassignedAt;
    @Column(length = 500)
    private String reason;

    protected TicketAssignment() {
    }

    public TicketAssignment(MaintenanceTicket t, UserAccount tech, UserAccount by, String reason) {
        ticket = t;
        technician = tech;
        assignedBy = by;
        this.reason = reason;
    }

    @PrePersist
    void create() {
        if (id == null)
            id = UUID.randomUUID();
        if (assignedAt == null)
            assignedAt = Instant.now();
    }

    public void unassign() {
        if (unassignedAt == null)
            unassignedAt = Instant.now();
    }
}
