package com.example.quanlybaotri.ticket.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sla_policies")
public class SlaPolicy {
    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 20)
    private TicketPriority priority;
    @Column(name = "response_minutes", nullable = false)
    private int responseMinutes;
    @Column(name = "resolution_minutes", nullable = false)
    private int resolutionMinutes;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected SlaPolicy() {
    }

    public void update(int response, int resolution, boolean active) {
        this.responseMinutes = response;
        this.resolutionMinutes = resolution;
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public int getResponseMinutes() {
        return responseMinutes;
    }

    public int getResolutionMinutes() {
        return resolutionMinutes;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }
}
