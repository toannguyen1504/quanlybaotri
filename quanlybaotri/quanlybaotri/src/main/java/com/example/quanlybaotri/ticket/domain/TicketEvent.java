package com.example.quanlybaotri.ticket.domain;

import com.example.quanlybaotri.identity.domain.UserAccount;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_events")
public class TicketEvent {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id")
    private MaintenanceTicket ticket;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private UserAccount actor;
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private TicketStatus fromStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 30)
    private TicketStatus toStatus;
    @Column(nullable = false, columnDefinition = "text")
    private String description;
    @Column(name = "metadata_json", columnDefinition = "text")
    private String metadataJson;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TicketEvent() {
    }

    public TicketEvent(MaintenanceTicket ticket, UserAccount actor, String type, TicketStatus from, TicketStatus to,
            String description, String metadata) {
        this.id = UUID.randomUUID();
        this.ticket = ticket;
        this.actor = actor;
        this.eventType = type;
        this.fromStatus = from;
        this.toStatus = to;
        this.description = description;
        this.metadataJson = metadata;
    }

    @PrePersist
    void create() {
        if (id == null)
            id = UUID.randomUUID();
        if (createdAt == null)
            createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public MaintenanceTicket getTicket() {
        return ticket;
    }

    public UserAccount getActor() {
        return actor;
    }

    public String getEventType() {
        return eventType;
    }

    public TicketStatus getFromStatus() {
        return fromStatus;
    }

    public TicketStatus getToStatus() {
        return toStatus;
    }

    public String getDescription() {
        return description;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
