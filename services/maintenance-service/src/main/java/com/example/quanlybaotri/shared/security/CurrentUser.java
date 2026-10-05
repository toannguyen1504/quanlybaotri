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
        String customer = auth.getToken().getClaimAsString("customer_id");
        java.util.List<String> siteClaims = auth.getToken().getClaimAsStringList("site_ids");
        Set<UUID> siteIds = siteClaims == null ? Set.of() : siteClaims.stream().map(UUID::fromString).collect(Collectors.toUnmodifiableSet());
        return new Actor(
            UUID.fromString(uid),
            auth.getName(),
            auth.getToken().getClaimAsString("name"),
            roles,
            auth.getToken().getTokenValue(),
            auth.getToken().getClaimAsString("account_type"),
            customer == null || customer.isBlank() ? null : UUID.fromString(customer),
            auth.getToken().getClaimAsString("site_scope"),
            siteIds
        );
    }

    public record Actor(
        UUID id,
        String username,
        String fullName,
        Set<RoleName> roles,
        String token,
        String accountType,
        UUID customerId,
        String siteScope,
        Set<UUID> siteIds
    ) {
        public boolean has(RoleName role) {
            return roles.contains(role);
        }
        public boolean customer() { return "CUSTOMER".equals(accountType); }
        public boolean canAccess(UUID customer, UUID site) {
            return !customer() || (java.util.Objects.equals(customerId, customer) && ("ALL_SITES".equals(siteScope) || siteIds.contains(site)));
        }
    }
}
