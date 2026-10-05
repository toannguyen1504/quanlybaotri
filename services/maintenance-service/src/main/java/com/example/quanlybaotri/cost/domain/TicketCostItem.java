package com.example.quanlybaotri.cost.domain;

import com.example.quanlybaotri.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.math.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_cost_items")
public class TicketCostItem extends BaseEntity {
    @Column(name="ticket_id",nullable=false) private UUID ticketId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private CostCategory category;
    @Enumerated(EnumType.STRING) @Column(name="source_type",nullable=false,length=20) private CostSourceType sourceType;
    @Column(name="source_id") private UUID sourceId;
    @Enumerated(EnumType.STRING) @Column(name="calculation_status",nullable=false,length=20) private CalculationStatus calculationStatus;
    @Column(nullable=false,length=500) private String description;
    @Column(nullable=false,precision=19,scale=3) private BigDecimal quantity;
    @Column(nullable=false,length=30) private String unit;
    @Column(name="unit_cost",precision=19,scale=2) private BigDecimal unitCost;
    @Column(precision=19,scale=2) private BigDecimal amount;
    @Column(name="incurred_at",nullable=false) private Instant incurredAt;
    @Column(length=200) private String vendor;
    @Column(name="reference_number",length=100) private String referenceNumber;
    @Column(name="created_by_id") private UUID createdById;
    protected TicketCostItem() {}
    public static TicketCostItem manual(UUID ticketId, CostCategory category, String description, BigDecimal quantity, String unit, BigDecimal unitCost, Instant incurredAt, String vendor, String reference, UUID actor) {
        TicketCostItem item=new TicketCostItem(); item.ticketId=ticketId; item.sourceType=CostSourceType.MANUAL; item.sourceId=null; item.createdById=actor; item.apply(category,description,quantity,unit,unitCost,incurredAt,vendor,reference); return item;
    }
    public static TicketCostItem automatic(UUID ticketId, CostCategory category, CostSourceType source, UUID sourceId, String description, BigDecimal quantity, String unit, BigDecimal unitCost, Instant incurredAt, UUID actor) {
        TicketCostItem item=new TicketCostItem(); item.ticketId=ticketId; item.category=category; item.sourceType=source; item.sourceId=sourceId; item.description=description; item.quantity=quantity; item.unit=unit; item.incurredAt=incurredAt; item.createdById=actor; item.setRate(unitCost); return item;
    }
    public static TicketCostItem labor(UUID ticketId, UUID workLogId, String description, int minutes, BigDecimal hourlyRate, Instant incurredAt, UUID actor) {
        TicketCostItem item=new TicketCostItem(); item.ticketId=ticketId; item.category=CostCategory.LABOR; item.sourceType=CostSourceType.WORK_LOG; item.sourceId=workLogId; item.description=description; item.quantity=BigDecimal.valueOf(minutes); item.unit="PHÚT"; item.incurredAt=incurredAt; item.createdById=actor; item.unitCost=hourlyRate==null?null:hourlyRate.setScale(2,RoundingMode.HALF_UP); item.calculationStatus=hourlyRate==null?CalculationStatus.MISSING_RATE:CalculationStatus.READY; item.amount=hourlyRate==null?null:hourlyRate.multiply(BigDecimal.valueOf(minutes)).divide(BigDecimal.valueOf(60),2,RoundingMode.HALF_UP); return item;
    }
    public void apply(CostCategory category,String description,BigDecimal quantity,String unit,BigDecimal unitCost,Instant incurredAt,String vendor,String reference){this.category=category;this.description=description.trim();this.quantity=quantity;this.unit=unit.trim().toUpperCase();this.incurredAt=incurredAt;this.vendor=blank(vendor);this.referenceNumber=blank(reference);setRate(unitCost);}
    public void setRate(BigDecimal rate){unitCost=rate==null?null:rate.setScale(2,RoundingMode.HALF_UP);calculationStatus=rate==null?CalculationStatus.MISSING_RATE:CalculationStatus.READY;amount=rate==null?null:quantity.multiply(unitCost).setScale(2,RoundingMode.HALF_UP);}
    public void adjustMigrationRemainder(BigDecimal value){if(sourceType!=CostSourceType.MIGRATION)throw new IllegalStateException("Only migration rows can be adjusted");description="Chênh lệch chi phí linh kiện từ hệ thống cũ";quantity=BigDecimal.ONE;unit="GÓI";setRate(value);}
    private String blank(String v){return v==null||v.isBlank()?null:v.trim();}
    public UUID getTicketId(){return ticketId;} public CostCategory getCategory(){return category;} public CostSourceType getSourceType(){return sourceType;} public UUID getSourceId(){return sourceId;} public CalculationStatus getCalculationStatus(){return calculationStatus;} public String getDescription(){return description;} public BigDecimal getQuantity(){return quantity;} public String getUnit(){return unit;} public BigDecimal getUnitCost(){return unitCost;} public BigDecimal getAmount(){return amount;} public Instant getIncurredAt(){return incurredAt;} public String getVendor(){return vendor;} public String getReferenceNumber(){return referenceNumber;} public UUID getCreatedById(){return createdById;}
}
