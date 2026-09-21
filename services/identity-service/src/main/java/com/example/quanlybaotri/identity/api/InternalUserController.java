package com.example.quanlybaotri.identity.api;

import com.example.quanlybaotri.identity.domain.*;
import com.example.quanlybaotri.identity.persistence.UserRepository;
import com.example.quanlybaotri.shared.api.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/users")
public class InternalUserController {

    private final UserRepository users;

    public InternalUserController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/{id}")
    public UserRef get(@PathVariable UUID id) {
        return UserRef.from(
            users
                .findWithRolesById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"))
        );
    }

    @PostMapping("/resolve")
    public List<UserRef> resolve(@Valid @RequestBody ResolveRequest r) {
        return users.findByIdIn(new LinkedHashSet<>(r.ids())).stream().map(UserRef::from).toList();
    }

    @PostMapping("/search")
    public List<UserRef> search(@RequestBody SearchRequest r) {
        Set<RoleName> roles = r.roles() == null ? Set.of() : r.roles();
        if (roles.isEmpty()) return List.of();
        return (
            r.enabledOnly() ? users.findEnabledByRoleNames(roles) : users.findByRoleNames(roles)
        )
            .stream()
            .map(UserRef::from)
            .toList();
    }

    public record ResolveRequest(@Size(max = 500) List<UUID> ids) {
        public ResolveRequest {
            ids = ids == null ? List.of() : List.copyOf(ids);
        }
    }

    public record SearchRequest(Set<RoleName> roles, boolean enabledOnly) {}

    public record UserRef(
        UUID id,
        String username,
        String fullName,
        String email,
        UUID departmentId,
        boolean enabled,
        Set<RoleName> roles
    ) {
        static UserRef from(UserAccount u) {
            return new UserRef(
                u.getId(),
                u.getUsername(),
                u.getFullName(),
                u.getEmail(),
                u.getDepartmentId(),
                u.isEnabled(),
                u
                    .getRoles()
                    .stream()
                    .map(Role::getName)
                    .collect(java.util.stream.Collectors.toSet())
            );
        }
    }
}
