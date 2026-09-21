package com.example.quanlybaotri.inventory.domain;

import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "parts")
public class Part extends BaseEntity {

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 50)
    private String unit;

    @Column(name = "current_stock", nullable = false, precision = 19, scale = 3)
    private BigDecimal currentStock = BigDecimal.ZERO;

    @Column(name = "minimum_stock", nullable = false, precision = 19, scale = 3)
    private BigDecimal minimumStock = BigDecimal.ZERO;

    @Column(name = "default_unit_cost", nullable = false, precision = 19, scale = 2)
    private BigDecimal defaultUnitCost = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;

    protected Part() {}

    public Part(String code, String name, String unit, BigDecimal min, BigDecimal cost) {
        this.code = code;
        this.name = name;
        this.unit = unit;
        this.minimumStock = min;
        this.defaultUnitCost = cost;
    }

    public void update(
        String code,
        String name,
        String unit,
        BigDecimal min,
        BigDecimal cost,
        boolean active
    ) {
        this.code = code;
        this.name = name;
        this.unit = unit;
        this.minimumStock = min;
        this.defaultUnitCost = cost;
        this.active = active;
    }

    public void add(BigDecimal q) {
        currentStock = currentStock.add(q);
    }

    public void issue(BigDecimal q) {
        if (currentStock.compareTo(q) < 0) throw ApiException.conflict(
            "Không đủ tồn kho cho linh kiện " + code
        );
        currentStock = currentStock.subtract(q);
    }

    public void adjust(BigDecimal value) {
        currentStock = value;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getUnit() {
        return unit;
    }

    public BigDecimal getCurrentStock() {
        return currentStock;
    }

    public BigDecimal getMinimumStock() {
        return minimumStock;
    }

    public BigDecimal getDefaultUnitCost() {
        return defaultUnitCost;
    }

    public boolean isActive() {
        return active;
    }
}
