package com.example.quanlybaotri.ticket.domain;

import com.example.quanlybaotri.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "work_logs")
public class WorkLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id")
    private MaintenanceTicket ticket;

    @Column(name = "technician_id", nullable = false)
    private UUID technicianId;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "minutes_spent", nullable = false)
    private int minutesSpent;

    protected WorkLog() {}

    public WorkLog(MaintenanceTicket t, UUID tech, String content, int minutes) {
        ticket = t;
        technicianId = tech;
        this.content = content;
        minutesSpent = minutes;
    }

    public MaintenanceTicket getTicket() {
        return ticket;
    }

    public UUID getTechnicianId() {
        return technicianId;
    }

    public String getContent() {
        return content;
    }

    public int getMinutesSpent() {
        return minutesSpent;
    }
}
