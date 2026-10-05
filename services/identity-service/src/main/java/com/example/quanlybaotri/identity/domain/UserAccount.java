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

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

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
}
