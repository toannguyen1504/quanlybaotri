package com.example.quanlybaotri.equipment.domain;

import com.example.quanlybaotri.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "equipment_categories")
public class EquipmentCategory extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    protected EquipmentCategory() {}

    public EquipmentCategory(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = normalized(description);
    }

    public void update(String code, String name, String description, boolean active) {
        this.code = code;
        this.name = name;
        this.description = normalized(description);
        this.active = active;
    }

    private String normalized(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }
}
