package com.example.quanlybaotri.ticket.domain;

import com.example.quanlybaotri.equipment.domain.Equipment;
import com.example.quanlybaotri.identity.domain.UserAccount;
import com.example.quanlybaotri.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import org.hibernate.annotations.Formula;

@Entity
@Table(name = "tickets")
public class MaintenanceTicket extends BaseEntity {
    @Column(nullable = false, unique = true, length = 30)
    private String code;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id")
    private Equipment equipment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id")
    private UserAccount requester;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private UserAccount assignee;
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

    protected MaintenanceTicket() {
    }

    public MaintenanceTicket(String code, Equipment equipment, UserAccount requester, String title, String description,
            TicketPriority priority, Instant responseDue, Instant resolutionDue) {
        this(code, equipment, requester, title, description, priority, responseDue, resolutionDue,
                TicketChargeType.PENDING);
    }

    public MaintenanceTicket(String code, Equipment equipment, UserAccount requester, String title, String description,
            TicketPriority priority, Instant responseDue, Instant resolutionDue, TicketChargeType chargeType) {
        this.code = code;
        this.equipment = equipment;
        this.requester = requester;
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.status = TicketStatus.SUBMITTED;
        this.submittedAt = Instant.now();
        this.responseDueAt = responseDue;
        this.resolutionDueAt = resolutionDue;
        this.chargeType = chargeType == null ? TicketChargeType.PENDING : chargeType;
        this.partsCost = BigDecimal.ZERO.setScale(2);
    }

    public void priority(TicketPriority p) {
        priority = p;
    }

    public void updateSlaDeadlines(Instant responseDue, Instant resolutionDue) {
        this.responseDueAt = responseDue;
        this.resolutionDueAt = resolutionDue;
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

    public void assign(UserAccount user) {
        assignee = user;
        status = TicketStatus.ASSIGNED;
    }

    public void start() {
        status = TicketStatus.IN_PROGRESS;
        if (startedAt == null)
            startedAt = Instant.now();
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

    public void chargeType(TicketChargeType value) {
        chargeType = value;
    }

    public void addPartCost(BigDecimal value) {
        if (value == null || value.signum() < 0)
            throw new IllegalArgumentException("Part cost cannot be negative");
        partsCost = partsCost.add(value).setScale(2, RoundingMode.HALF_UP);
    }

    public String getCode() {
        return code;
    }

    public Equipment getEquipment() {
        return equipment;
    }

    public UserAccount getRequester() {
        return requester;
    }

    public UserAccount getAssignee() {
        return assignee;
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
