package com.example.quanlybaotri.shared.security;

import com.example.quanlybaotri.identity.domain.RoleName;
import com.example.quanlybaotri.shared.api.ApiException;
import java.util.*;
import java.util.stream.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    public Actor require() {
        if (
            !(SecurityContextHolder.getContext().getAuthentication() instanceof
                JwtAuthenticationToken a)
        ) throw ApiException.forbidden("Yêu cầu token người dùng");
        String uid = a.getToken().getClaimAsString("uid");
        if (
            !"user".equals(a.getToken().getClaimAsString("token_type")) || uid == null
        ) throw ApiException.forbidden("Yêu cầu token người dùng");
        Set<RoleName> roles = a
            .getToken()
            .getClaimAsStringList("roles")
            .stream()
            .map(v -> v.replaceFirst("^ROLE_", ""))
            .map(RoleName::valueOf)
            .collect(Collectors.toUnmodifiableSet());
        return new Actor(
            UUID.fromString(uid),
            a.getName(),
            a.getToken().getClaimAsString("name"),
            roles,
            a.getToken().getTokenValue()
        );
    }

    public record Actor(
        UUID id,
        String username,
        String fullName,
        Set<RoleName> roles,
        String token
    ) {
        public boolean has(RoleName role) {
            return roles.contains(role);
        }
    }
}
