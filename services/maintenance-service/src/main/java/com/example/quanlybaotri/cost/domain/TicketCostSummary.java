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
    @Column(name = "other_total", nullable = false) private BigDecimal otherTotal;
    @Column(name = "grand_total", nullable = false) private BigDecimal grandTotal;
    @Column(name = "quoted_by_id") private UUID quotedById;
    @Column(name = "quoted_at") private Instant quotedAt;
    @Column(name = "accepted_by_id") private UUID acceptedById;
    @Column(name = "accepted_at") private Instant acceptedAt;
    @Column(name = "declined_by_id") private UUID declinedById;
    @Column(name = "declined_at") private Instant declinedAt;
    @Column(name = "decline_reason", length = 1000) private String declineReason;
    @Column(name = "legacy_charge_type", length = 10) private String legacyChargeType;
    @Column(name = "legacy_parts_cost") private BigDecimal legacyPartsCost;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    protected TicketCostSummary() {}
    public TicketCostSummary(UUID ticketId) {
        this.ticketId = ticketId; status = CostStatus.DRAFT;
        partsTotal = otherTotal = grandTotal = BigDecimal.ZERO.setScale(2);
        createdAt = updatedAt = Instant.now();
    }
    public void totals(BigDecimal parts, BigDecimal other) {
        if (status != CostStatus.DRAFT) throw new IllegalStateException("Only draft cost summaries can be recalculated");
        partsTotal=money(parts); otherTotal=money(other);
        grandTotal=partsTotal.add(otherTotal); updatedAt=Instant.now();
    }
    public void quote(UUID actor) { status=CostStatus.QUOTED; quotedById=actor; quotedAt=Instant.now(); declinedById=null; declinedAt=null; declineReason=null; acceptedById=null; acceptedAt=null; updatedAt=Instant.now(); }
    public void accept(UUID actor) { status=CostStatus.ACCEPTED; acceptedById=actor; acceptedAt=Instant.now(); updatedAt=Instant.now(); }
    public void decline(UUID actor, String reason) { status=CostStatus.DRAFT; declinedById=actor; declinedAt=Instant.now(); declineReason=reason; acceptedById=null; acceptedAt=null; updatedAt=Instant.now(); }
    public void reopen(String reason) { if (status != CostStatus.DRAFT) { status=CostStatus.DRAFT; declineReason=reason; acceptedById=null; acceptedAt=null; updatedAt=Instant.now(); } }
    private BigDecimal money(BigDecimal value) { return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2); }
    public UUID getTicketId(){return ticketId;} public CostStatus getStatus(){return status;} public BigDecimal getPartsTotal(){return partsTotal;} public BigDecimal getOtherTotal(){return otherTotal;} public BigDecimal getGrandTotal(){return grandTotal;} public UUID getQuotedById(){return quotedById;} public Instant getQuotedAt(){return quotedAt;} public UUID getAcceptedById(){return acceptedById;} public Instant getAcceptedAt(){return acceptedAt;} public UUID getDeclinedById(){return declinedById;} public Instant getDeclinedAt(){return declinedAt;} public String getDeclineReason(){return declineReason;}
    public BigDecimal getLegacyPartsCost(){return legacyPartsCost;}
}
