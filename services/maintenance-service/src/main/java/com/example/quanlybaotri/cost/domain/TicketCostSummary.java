package com.example.quanlybaotri.cost.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_cost_summaries")
public class TicketCostSummary {
    @Id @Column(name = "ticket_id") private UUID ticketId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private CostStatus status;
    @Column(name = "parts_total", nullable = false) private BigDecimal partsTotal;
    @Column(name = "labor_total", nullable = false) private BigDecimal laborTotal;
    @Column(name = "outsourced_total", nullable = false) private BigDecimal outsourcedTotal;
    @Column(name = "travel_total", nullable = false) private BigDecimal travelTotal;
    @Column(name = "other_total", nullable = false) private BigDecimal otherTotal;
    @Column(name = "grand_total", nullable = false) private BigDecimal grandTotal;
    @Column(name = "submitted_by_id") private UUID submittedById;
    @Column(name = "submitted_at") private Instant submittedAt;
    @Column(name = "approved_by_id") private UUID approvedById;
    @Column(name = "approved_at") private Instant approvedAt;
    @Column(name = "rejected_by_id") private UUID rejectedById;
    @Column(name = "rejected_at") private Instant rejectedAt;
    @Column(name = "rejection_reason", length = 1000) private String rejectionReason;
    @Column(name = "legacy_charge_type", length = 10) private String legacyChargeType;
    @Column(name = "legacy_parts_cost") private BigDecimal legacyPartsCost;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    protected TicketCostSummary() {}
    public TicketCostSummary(UUID ticketId) {
        this.ticketId = ticketId; status = CostStatus.DRAFT;
        partsTotal = laborTotal = outsourcedTotal = travelTotal = otherTotal = grandTotal = BigDecimal.ZERO.setScale(2);
        createdAt = updatedAt = Instant.now();
    }
    public void totals(BigDecimal parts, BigDecimal labor, BigDecimal outsourced, BigDecimal travel, BigDecimal other) {
        if (status == CostStatus.APPROVED) throw new IllegalStateException("Approved cost summary is immutable");
        partsTotal=money(parts); laborTotal=money(labor); outsourcedTotal=money(outsourced); travelTotal=money(travel); otherTotal=money(other);
        grandTotal=partsTotal.add(laborTotal).add(outsourcedTotal).add(travelTotal).add(otherTotal); updatedAt=Instant.now();
    }
    public void submit(UUID actor) { status=CostStatus.READY; submittedById=actor; submittedAt=Instant.now(); rejectedById=null; rejectedAt=null; rejectionReason=null; updatedAt=Instant.now(); }
    public void approve(UUID actor) { status=CostStatus.APPROVED; approvedById=actor; approvedAt=Instant.now(); updatedAt=Instant.now(); }
    public void reject(UUID actor, String reason) { status=CostStatus.DRAFT; rejectedById=actor; rejectedAt=Instant.now(); rejectionReason=reason; approvedById=null; approvedAt=null; updatedAt=Instant.now(); }
    public void reopen(String reason) { if (status != CostStatus.DRAFT) { status=CostStatus.DRAFT; rejectionReason=reason; approvedById=null; approvedAt=null; updatedAt=Instant.now(); } }
    private BigDecimal money(BigDecimal value) { return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2); }
    public UUID getTicketId(){return ticketId;} public CostStatus getStatus(){return status;} public BigDecimal getPartsTotal(){return partsTotal;} public BigDecimal getLaborTotal(){return laborTotal;} public BigDecimal getOutsourcedTotal(){return outsourcedTotal;} public BigDecimal getTravelTotal(){return travelTotal;} public BigDecimal getOtherTotal(){return otherTotal;} public BigDecimal getGrandTotal(){return grandTotal;} public UUID getSubmittedById(){return submittedById;} public Instant getSubmittedAt(){return submittedAt;} public UUID getApprovedById(){return approvedById;} public Instant getApprovedAt(){return approvedAt;} public UUID getRejectedById(){return rejectedById;} public Instant getRejectedAt(){return rejectedAt;} public String getRejectionReason(){return rejectionReason;}
    public BigDecimal getLegacyPartsCost(){return legacyPartsCost;}
}
