package com.example.quanlybaotri.identity.api;

import com.example.quanlybaotri.identity.application.UserService;
import com.example.quanlybaotri.identity.domain.RoleName;
import com.example.quanlybaotri.identity.domain.UserAccount;
import com.example.quanlybaotri.identity.persistence.RoleRepository;
import com.example.quanlybaotri.shared.security.CurrentUser;
import com.example.quanlybaotri.organization.client.OrganizationClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class UserController {
    private final UserService service;
    private final RoleRepository roles;
    private final CurrentUser current;
    private final OrganizationClient organizations;

    public UserController(UserService service, RoleRepository roles, CurrentUser current,
            OrganizationClient organizations) {
        this.service = service;
        this.roles = roles;
        this.current = current;
        this.organizations = organizations;
    }

    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Page<UserView> list(Pageable pageable) {
        return service.list(pageable);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public UserView create(@Valid @RequestBody UserRequest request) {
        return service.create(request);
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserView update(@PathVariable UUID id, @Valid @RequestBody UserRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/users/{id}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void reset(@PathVariable UUID id, @Valid @RequestBody PasswordReset request) {
        service.resetPassword(id, request.password());
    }

    @PostMapping("/users/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void change(@Valid @RequestBody PasswordChange request) {
        service.changePassword(current.require().getId(), request.currentPassword(), request.newPassword());
    }

    @PutMapping("/users/me/profile")
    public AuthController.MeResponse updateCurrentProfile(@Valid @RequestBody CurrentProfileUpdate request) {
        UserAccount actor = current.require();
        UserAccount updated = service.updateCurrentProfile(actor.getId(), request.email(), request.fullName(), request.phone());
        OrganizationClient.DepartmentRef department = updated.getDepartmentId() == null ? null
                : organizations.find(updated.getDepartmentId());
        return AuthController.MeResponse.from(updated, department);
    }

    @GetMapping("/roles")
    public List<RoleView> roleList() {
        return roles.findAll().stream().map(r -> new RoleView(r.getId(), r.getName(), r.getDescription())).toList();
    }

    public record UserRequest(@NotBlank @Size(max = 80) String username, @NotBlank @Email String email,
            @Size(min = 8, max = 100) String password, @NotBlank @Size(max = 150) String fullName,
            @Size(max = 30) String phone, UUID departmentId, Set<RoleName> roles, boolean enabled) {
    }

    public record PasswordReset(@NotBlank @Size(min = 8, max = 100) String password) {
    }

    public record PasswordChange(@NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 100) String newPassword) {
    }

    public record CurrentProfileUpdate(@NotBlank @Email String email,
            @NotBlank @Size(max = 150) String fullName, @Size(max = 30) String phone) {
    }

    public record RoleView(UUID id, RoleName name, String description) {
    }

    public record UserView(UUID id, String username, String email, String fullName, String phone, UUID departmentId,
            String departmentName, boolean enabled, boolean mustChangePassword, Set<RoleName> roles) {
        public static UserView from(UserAccount u,OrganizationClient.DepartmentRef department) {
            return new UserView(u.getId(), u.getUsername(), u.getEmail(), u.getFullName(), u.getPhone(),
                    u.getDepartmentId(), department==null?null:department.name(), u.isEnabled(),
                    u.isMustChangePassword(),
                    u.getRoles().stream().map(r -> r.getName()).collect(java.util.stream.Collectors.toSet()));
        }
    }
}
