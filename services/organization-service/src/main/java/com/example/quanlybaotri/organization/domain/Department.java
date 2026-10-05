package com.example.quanlybaotri.organization.domain;

import com.example.quanlybaotri.shared.domain.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "customer_sites")
public class Department extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id")
    private Region region;

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

    protected Department() {}

    public Department(Branch branch, String code, String name) {
        this.branch = branch;
        this.code = code;
        this.name = name;
    }

    public void update(
        String code,
        String name,
        String description,
        String location,
        String email,
        String phone,
        boolean active
    ) {
        this.code = code;
        this.name = name;
        this.description = normalize(description);
        this.location = normalize(location);
        this.contactEmail = normalize(email);
        if (this.contactEmail != null) this.contactEmail = this.contactEmail.toLowerCase();
        this.contactPhone = normalize(phone);
        this.active = active;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public String getCode() {
        return code;
    }

    public Branch getBranch() { return branch; }

    public void branch(Branch branch) { this.branch = branch; }

    public Region getRegion() { return region; }

    public void region(Region region) { this.region = region; }

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
