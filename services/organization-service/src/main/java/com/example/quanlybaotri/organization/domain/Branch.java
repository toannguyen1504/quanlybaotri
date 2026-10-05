package com.example.quanlybaotri.organization.domain;

import com.example.quanlybaotri.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "customers")
public class Branch extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String address;

    @Column(name = "contact_email", length = 190)
    private String contactEmail;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Column(nullable = false)
    private boolean active = true;

    protected Branch() {}

    public Branch(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public void update(String code, String name, String address, String email, String phone, boolean active) {
        this.code = code;
        this.name = name;
        this.address = normalize(address);
        this.contactEmail = normalize(email);
        if (contactEmail != null) contactEmail = contactEmail.toLowerCase();
        this.contactPhone = normalize(phone);
        this.active = active;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getContactEmail() { return contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public boolean isActive() { return active; }
}
