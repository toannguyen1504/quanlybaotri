package com.example.quanlybaotri.identity.domain;

import com.example.quanlybaotri.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
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
    @Column(name = "department_id")
    private java.util.UUID departmentId;
    @Column(nullable = false)
    private boolean enabled = true;
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = true;
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    protected UserAccount() {
    }

    public UserAccount(String username, String email, String passwordHash, String fullName) {
        this.username = username.toLowerCase();
        this.email = email.toLowerCase();
        this.passwordHash = passwordHash;
        this.fullName = fullName;
    }

    public void updateProfile(String email, String fullName, String phone, java.util.UUID departmentId) {
        this.email = email.toLowerCase();
        this.fullName = fullName;
        this.phone = phone;
        this.departmentId = departmentId;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = new HashSet<>(roles);
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

    public java.util.UUID getDepartmentId() {
        return departmentId;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }
}
