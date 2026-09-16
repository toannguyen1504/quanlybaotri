package com.example.quanlybaotri.ticket.domain;

import com.example.quanlybaotri.identity.domain.UserAccount;
import com.example.quanlybaotri.shared.domain.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "work_logs")
public class WorkLog extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id")
    private MaintenanceTicket ticket;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "technician_id")
    private UserAccount technician;
    @Column(nullable = false, columnDefinition = "text")
    private String content;
    @Column(name = "minutes_spent", nullable = false)
    private int minutesSpent;

    protected WorkLog() {
    }

    public WorkLog(MaintenanceTicket ticket, UserAccount tech, String content, int minutes) {
        this.ticket = ticket;
        this.technician = tech;
        this.content = content;
        this.minutesSpent = minutes;
    }

    public MaintenanceTicket getTicket() {
        return ticket;
    }

    public UserAccount getTechnician() {
        return technician;
    }

    public String getContent() {
        return content;
    }

    public int getMinutesSpent() {
        return minutesSpent;
    }
}
