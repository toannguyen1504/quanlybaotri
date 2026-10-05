package com.example.quanlybaotri.shared.security;

import com.example.quanlybaotri.shared.api.ApiException;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
    public Actor require(){
        if(!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken auth))throw ApiException.forbidden("Yêu cầu token người dùng");
        var jwt=auth.getToken();if(!"user".equals(jwt.getClaimAsString("token_type")))throw ApiException.forbidden("Yêu cầu token người dùng");
        String customer=jwt.getClaimAsString("customer_id");List<String> sites=jwt.getClaimAsStringList("site_ids");
        return new Actor("CUSTOMER".equals(jwt.getClaimAsString("account_type")),customer==null||customer.isBlank()?null:UUID.fromString(customer),jwt.getClaimAsString("site_scope"),sites==null?Set.of():sites.stream().map(UUID::fromString).collect(Collectors.toUnmodifiableSet()));
    }
    public record Actor(boolean customer,UUID customerId,String siteScope,Set<UUID> siteIds){}
}
