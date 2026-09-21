package com.example.quanlybaotri.ticket.domain;

import com.example.quanlybaotri.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.math.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Formula;

@Entity
@Table(name = "tickets")
public class MaintenanceTicket extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TicketStatus status;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "response_due_at", nullable = false)
    private Instant responseDueAt;

    @Column(name = "resolution_due_at", nullable = false)
    private Instant resolutionDueAt;

    @Formula("submitted_at + (resolution_due_at - submitted_at) * 0.8")
    private Instant resolutionWarningAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "resolution_summary", columnDefinition = "text")
    private String resolutionSummary;

    @Enumerated(EnumType.STRING)
    @Column(name = "charge_type", nullable = false, length = 10)
    private TicketChargeType chargeType;

    @Column(name = "parts_cost", nullable = false, precision = 19, scale = 2)
    private BigDecimal partsCost;

    protected MaintenanceTicket() {}

    public MaintenanceTicket(
        String code,
        UUID equipmentId,
        UUID requesterId,
        String title,
        String description,
        TicketPriority priority,
        Instant responseDue,
        Instant resolutionDue
    ) {
        this.code = code;
        this.equipmentId = equipmentId;
        this.requesterId = requesterId;
        this.title = title;
        this.description = description;
        this.priority = priority;
        status = TicketStatus.SUBMITTED;
        submittedAt = Instant.now();
        responseDueAt = responseDue;
        resolutionDueAt = resolutionDue;
        chargeType = TicketChargeType.PENDING;
        partsCost = BigDecimal.ZERO.setScale(2);
    }

    public void priority(TicketPriority p) {
        priority = p;
    }

    public void updateSlaDeadlines(Instant r, Instant d) {
        responseDueAt = r;
        resolutionDueAt = d;
    }

    public void accept() {
        status = TicketStatus.ACCEPTED;
        acceptedAt = Instant.now();
    }

    public void reject() {
        status = TicketStatus.REJECTED;
    }

    public void cancel() {
        status = TicketStatus.CANCELLED;
    }

    public void assign(UUID id) {
        assigneeId = id;
        status = TicketStatus.ASSIGNED;
    }

    public void start() {
        status = TicketStatus.IN_PROGRESS;
        if (startedAt == null) startedAt = Instant.now();
    }

    public void waitParts() {
        status = TicketStatus.WAITING_PARTS;
    }

    public void resolve(String summary) {
        status = TicketStatus.RESOLVED;
        resolutionSummary = summary;
        resolvedAt = Instant.now();
    }

    public void reopen() {
        status = TicketStatus.IN_PROGRESS;
        resolvedAt = null;
        closedAt = null;
        chargeType = TicketChargeType.PENDING;
    }

    public void close() {
        status = TicketStatus.CLOSED;
        closedAt = Instant.now();
    }

    public void chargeType(TicketChargeType v) {
        chargeType = v;
    }

    public void addPartCost(BigDecimal v) {
        if (v == null || v.signum() < 0) throw new IllegalArgumentException(
            "Part cost cannot be negative"
        );
        partsCost = partsCost.add(v).setScale(2, RoundingMode.HALF_UP);
    }

    public String getCode() {
        return code;
    }

    public UUID getEquipmentId() {
        return equipmentId;
    }

    public UUID getRequesterId() {
        return requesterId;
    }

    public UUID getAssigneeId() {
        return assigneeId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getResponseDueAt() {
        return responseDueAt;
    }

    public Instant getResolutionDueAt() {
        return resolutionDueAt;
    }

    public Instant getResolutionWarningAt() {
        return resolutionWarningAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public String getResolutionSummary() {
        return resolutionSummary;
    }

    public TicketChargeType getChargeType() {
        return chargeType;
    }

    public BigDecimal getPartsCost() {
        return partsCost;
    }

    public BigDecimal getChargeAmount() {
        return chargeType == TicketChargeType.PAID ? partsCost : BigDecimal.ZERO.setScale(2);
    }
}
