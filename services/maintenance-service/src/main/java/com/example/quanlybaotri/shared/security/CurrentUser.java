package com.example.quanlybaotri.shared.security;

import com.example.quanlybaotri.identity.domain.RoleName;
import com.example.quanlybaotri.shared.api.ApiException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    public Actor require() {
        if (
            !(SecurityContextHolder.getContext().getAuthentication() instanceof
                JwtAuthenticationToken auth)
        ) throw ApiException.forbidden("Yêu cầu token người dùng");
        String tokenType = auth.getToken().getClaimAsString("token_type");
        String uid = auth.getToken().getClaimAsString("uid");
        if (!"user".equals(tokenType) || uid == null) throw ApiException.forbidden(
            "Yêu cầu token người dùng"
        );
        Set<RoleName> roles = auth
            .getToken()
            .getClaimAsStringList("roles")
            .stream()
            .map(value -> value.replaceFirst("^ROLE_", ""))
            .map(RoleName::valueOf)
            .collect(Collectors.toUnmodifiableSet());
        return new Actor(
            UUID.fromString(uid),
            auth.getName(),
            auth.getToken().getClaimAsString("name"),
            roles,
            auth.getToken().getTokenValue()
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
