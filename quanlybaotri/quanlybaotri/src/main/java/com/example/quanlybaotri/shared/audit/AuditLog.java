package com.example.quanlybaotri.shared.audit;

import com.example.quanlybaotri.identity.domain.UserAccount;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private UserAccount actor;
    @Column(nullable = false, length = 100)
    private String action;
    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;
    @Column(name = "entity_id")
    private UUID entityId;
    @Column(name = "details_json", columnDefinition = "text")
    private String detailsJson;
    @Column(name = "ip_address", length = 64)
    private String ipAddress;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditLog() {
    }

    public AuditLog(UserAccount a, String action, String entityType, String details, String ip) {
        id = UUID.randomUUID();
        actor = a;
        this.action = action;
        this.entityType = entityType;
        detailsJson = details;
        ipAddress = ip;
        createdAt = Instant.now();
    }
}
