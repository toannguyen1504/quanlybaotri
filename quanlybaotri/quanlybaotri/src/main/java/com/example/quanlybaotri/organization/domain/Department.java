package com.example.quanlybaotri.organization.domain;

import com.example.quanlybaotri.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "departments")
public class Department extends BaseEntity {
    @Column(nullable = false, unique = true, length = 50)
    private String code;
    @Column(nullable = false, length = 150)
    private String name;
    @Column(length = 500)
    private String description;
    @Column(length = 255)
    private String location;
    @Column(name = "contact_email", length = 190)
    private String contactEmail;
    @Column(name = "contact_phone", length = 30)
    private String contactPhone;
    @Column(nullable = false)
    private boolean active = true;

    protected Department() {
    }

    public Department(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public void update(String code, String name, String description, String location, String contactEmail,
            String contactPhone, boolean active) {
        this.code = code;
        this.name = name;
        this.description = normalized(description);
        this.location = normalized(location);
        this.contactEmail = normalized(contactEmail);
        if (this.contactEmail != null)
            this.contactEmail = this.contactEmail.toLowerCase();
        this.contactPhone = normalized(contactPhone);
        this.active = active;
    }

    private String normalized(String value) {
        if (value == null || value.isBlank())
            return null;
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

    public String getLocation() {
        return location;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public boolean isActive() {
        return active;
    }
}
