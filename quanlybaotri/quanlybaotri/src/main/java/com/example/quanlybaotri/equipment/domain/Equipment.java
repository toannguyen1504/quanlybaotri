package com.example.quanlybaotri.equipment.domain;

import com.example.quanlybaotri.organization.domain.Department;
import com.example.quanlybaotri.shared.domain.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "equipment")
public class Equipment extends BaseEntity {
    @Column(name = "asset_code", nullable = false, unique = true, length = 80)
    private String assetCode;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(name = "serial_number", length = 150)
    private String serialNumber;
    @Column(length = 150)
    private String manufacturer;
    @Column(length = 150)
    private String model;
    @Column(length = 255)
    private String location;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private EquipmentCategory category;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EquipmentStatus status = EquipmentStatus.ACTIVE;
    @Column(nullable = false)
    private boolean active = true;

    protected Equipment() {
    }

    public Equipment(String code, String name, EquipmentCategory category) {
        this.assetCode = code;
        this.name = name;
        this.category = category;
    }

    public void update(String code, String name, String serial, String manufacturer, String model, String location,
            EquipmentCategory category, Department department, EquipmentStatus status, boolean active) {
        this.assetCode = code;
        this.name = name;
        this.serialNumber = serial;
        this.manufacturer = manufacturer;
        this.model = model;
        this.location = location;
        this.category = category;
        this.department = department;
        this.status = status;
        this.active = active;
    }

    public void status(EquipmentStatus status) {
        this.status = status;
    }

    public String getAssetCode() {
        return assetCode;
    }

    public String getName() {
        return name;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public String getModel() {
        return model;
    }

    public String getLocation() {
        return location;
    }

    public EquipmentCategory getCategory() {
        return category;
    }

    public Department getDepartment() {
        return department;
    }

    public EquipmentStatus getStatus() {
        return status;
    }

    public boolean isActive() {
        return active;
    }
}
