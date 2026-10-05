package com.example.quanlybaotri.cost.domain;

import com.example.quanlybaotri.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="labor_rates")
public class LaborRate extends BaseEntity {
    @Column(name="technician_id") private UUID technicianId;
    @Column(name="hourly_rate",nullable=false,precision=19,scale=2) private BigDecimal hourlyRate;
    @Column(name="effective_from",nullable=false) private Instant effectiveFrom;
    @Column(name="effective_to") private Instant effectiveTo;
    @Column(nullable=false) private boolean active;
    protected LaborRate() {}
    public LaborRate(UUID technicianId,BigDecimal hourlyRate,Instant from,Instant to){update(technicianId,hourlyRate,from,to,true);}
    public void update(UUID technicianId,BigDecimal hourlyRate,Instant from,Instant to,boolean active){this.technicianId=technicianId;this.hourlyRate=hourlyRate;this.effectiveFrom=from;this.effectiveTo=to;this.active=active;}
    public boolean applies(Instant at){return active&&!at.isBefore(effectiveFrom)&&(effectiveTo==null||at.isBefore(effectiveTo));}
    public UUID getTechnicianId(){return technicianId;} public BigDecimal getHourlyRate(){return hourlyRate;} public Instant getEffectiveFrom(){return effectiveFrom;} public Instant getEffectiveTo(){return effectiveTo;} public boolean isActive(){return active;}
}
