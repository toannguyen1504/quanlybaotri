package com.example.quanlybaotri.shared.security;

import com.example.quanlybaotri.shared.api.ApiException;
import java.util.UUID;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
    public Actor require() {
        if(!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token))
            throw ApiException.forbidden("Yêu cầu user token");
        String uid=token.getToken().getClaimAsString("uid");
        if(uid==null||!"user".equals(token.getToken().getClaimAsString("token_type")))
            throw ApiException.forbidden("Yêu cầu user token");
        return new Actor(UUID.fromString(uid),token.getName());
    }
    public record Actor(UUID id,String username){}
}
