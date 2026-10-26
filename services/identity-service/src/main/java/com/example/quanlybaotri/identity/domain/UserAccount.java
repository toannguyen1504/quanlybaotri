package com.example.quanlybaotri.identity.domain;

import com.example.quanlybaotri.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Entity
@Table(name = "users")
public class UserAccount extends BaseEntity implements UserDetails {

    @Column(nullable = false, unique = true, length = 80)
    private String username;

    @Column(nullable = false, unique = true, length = 190)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "google_subject", unique = true, length = 255)
    private String googleSubject;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(length = 30)
    private String phone;

    @Column(name = "legacy_site_id")
    private UUID legacySiteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType = AccountType.INTERNAL;

    @Column(name = "customer_id")
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "site_scope", nullable = false, length = 20)
    private SiteScope siteScope = SiteScope.ALL_SITES;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_site_access", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "site_id", nullable = false)
    private Set<UUID> siteIds = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "region_scope", nullable = false, length = 24)
    private RegionScope regionScope = RegionScope.UNASSIGNED;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_region_access", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "region_id", nullable = false)
    private Set<UUID> regionIds = new HashSet<>();

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_status", nullable = false, length = 20)
    private RegistrationStatus registrationStatus = RegistrationStatus.APPROVED;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_source", nullable = false, length = 20)
    private RegistrationSource registrationSource = RegistrationSource.ADMIN;

    @Column(name = "requested_organization_name", length = 190)
    private String requestedOrganizationName;

    @Column(name = "registration_note", length = 1000)
    private String registrationNote;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "registered_at")
    private Instant registeredAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by_id")
    private UUID reviewedById;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    protected UserAccount() {}

    public UserAccount(String username, String email, String passwordHash, String fullName) {
        this.username = username.toLowerCase();
        this.email = email.toLowerCase();
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.registeredAt = Instant.now();
    }

    public void updateProfile(
        String email,
        String fullName,
        String phone
    ) {
        this.email = email.toLowerCase();
        this.fullName = fullName;
        this.phone = phone;
    }

    public void configureAccess(
        AccountType accountType,
        UUID customerId,
        SiteScope siteScope,
        Set<UUID> siteIds
    ) {
        this.accountType = accountType;
        this.customerId = customerId;
        this.siteScope = siteScope;
        this.siteIds = new HashSet<>(siteIds == null ? Set.of() : siteIds);
        this.legacySiteId = null;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = new HashSet<>(roles);
    }

    public void configureRegions(RegionScope scope, Set<UUID> regionIds) {
        this.regionScope = scope == null ? RegionScope.UNASSIGNED : scope;
        this.regionIds = new HashSet<>(regionIds == null ? Set.of() : regionIds);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void changePassword(String hash, boolean mustChange) {
        this.passwordHash = hash;
        this.mustChangePassword = mustChange;
    }

    public void prepareSelfRegistration(
        RegistrationSource source,
        String organizationName,
        String note
    ) {
        registrationStatus = RegistrationStatus.PENDING;
        registrationSource = source;
        requestedOrganizationName = clean(organizationName);
        registrationNote = clean(note);
        rejectionReason = null;
        registeredAt = Instant.now();
        reviewedAt = null;
        reviewedById = null;
        accountType = AccountType.CUSTOMER;
        customerId = null;
        siteScope = SiteScope.SELECTED_SITES;
        siteIds.clear();
        regionScope = RegionScope.UNASSIGNED;
        regionIds.clear();
        roles.clear();
        enabled = false;
        mustChangePassword = false;
    }

    public void updateRegistrationRequest(String phone, String organizationName, String note) {
        this.phone = clean(phone);
        requestedOrganizationName = clean(organizationName);
        registrationNote = clean(note);
    }

    public void linkGoogle(String subject) {
        if (googleSubject != null && !googleSubject.equals(subject)) {
            throw new IllegalStateException("Tài khoản đã liên kết với Google khác");
        }
        googleSubject = subject;
    }

    public void approveRegistration(
        UUID customerId,
        SiteScope siteScope,
        Set<UUID> siteIds,
        Role requesterRole,
        UUID reviewerId
    ) {
        configureAccess(AccountType.CUSTOMER, customerId, siteScope, siteIds);
        configureRegions(RegionScope.UNASSIGNED, Set.of());
        setRoles(Set.of(requesterRole));
        registrationStatus = RegistrationStatus.APPROVED;
        rejectionReason = null;
        reviewedAt = Instant.now();
        reviewedById = reviewerId;
        enabled = true;
        mustChangePassword = false;
    }

    public void rejectRegistration(String reason, UUID reviewerId) {
        registrationStatus = RegistrationStatus.REJECTED;
        rejectionReason = clean(reason);
        reviewedAt = Instant.now();
        reviewedById = reviewerId;
        enabled = false;
        roles.clear();
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Override
    public Set<? extends GrantedAuthority> getAuthorities() {
        Set<GrantedAuthority> result = new HashSet<>();
        roles.forEach(r -> result.add(new SimpleGrantedAuthority("ROLE_" + r.getName().name())));
        return result;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public UUID getLegacySiteId() { return legacySiteId; }
    public AccountType getAccountType() { return accountType; }
    public UUID getCustomerId() { return customerId; }
    public SiteScope getSiteScope() { return siteScope; }
    public Set<UUID> getSiteIds() { return Set.copyOf(siteIds); }
    public RegionScope getRegionScope() { return regionScope; }
    public Set<UUID> getRegionIds() { return Set.copyOf(regionIds); }

    public Set<Role> getRoles() {
        return roles;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public boolean hasPassword() { return passwordHash != null && !passwordHash.isBlank(); }
    public boolean isGoogleLinked() { return googleSubject != null; }
    public String getGoogleSubject() { return googleSubject; }
    public RegistrationStatus getRegistrationStatus() { return registrationStatus; }
    public RegistrationSource getRegistrationSource() { return registrationSource; }
    public String getRequestedOrganizationName() { return requestedOrganizationName; }
    public String getRegistrationNote() { return registrationNote; }
    public String getRejectionReason() { return rejectionReason; }
    public Instant getRegisteredAt() { return registeredAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public UUID getReviewedById() { return reviewedById; }
}
