package com.example.quanlybaotri.ai.security;

import com.example.quanlybaotri.ai.shared.ApiException;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CurrentActor {
    public Actor require(){
        if(!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken auth))
            throw new ApiException(HttpStatus.FORBIDDEN,"USER_TOKEN_REQUIRED","Yêu cầu token người dùng");
        String uid=auth.getToken().getClaimAsString("uid");
        if(!"user".equals(auth.getToken().getClaimAsString("token_type"))||uid==null)
            throw new ApiException(HttpStatus.FORBIDDEN,"USER_TOKEN_REQUIRED","Yêu cầu token người dùng");
        List<String> claimed=auth.getToken().getClaimAsStringList("roles");
        Set<String> roles=new LinkedHashSet<>();
        if(claimed!=null) claimed.forEach(r->roles.add(r.replaceFirst("^ROLE_","")));
        return new Actor(UUID.fromString(uid),auth.getToken().getClaimAsString("name"),Set.copyOf(roles),
            auth.getToken().getClaimAsString("account_type"),auth.getToken().getTokenValue());
    }
    public record Actor(UUID id,String name,Set<String> roles,String accountType,String token){
        public boolean has(String role){return roles.contains(role);}
        public boolean elevated(){return has("ADMIN")||has("MANAGER");}
        public boolean customer(){return "CUSTOMER".equals(accountType);}
    }
}
