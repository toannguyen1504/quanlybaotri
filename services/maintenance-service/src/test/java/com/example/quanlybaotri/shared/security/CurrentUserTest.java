package com.example.quanlybaotri.shared.security;

import static org.assertj.core.api.Assertions.*;
import com.example.quanlybaotri.identity.domain.RoleName;
import com.example.quanlybaotri.shared.api.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CurrentUserTest {
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void readsActorOnlyFromUserJwt(){UUID id=UUID.randomUUID();Jwt jwt=Jwt.withTokenValue("token").header("alg","none")
            .subject("technician").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
            .claim("token_type","user").claim("uid",id.toString()).claim("name","Kỹ thuật viên")
            .claim("roles",List.of("ROLE_TECHNICIAN")).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        var actor=new CurrentUser().require();assertThat(actor.id()).isEqualTo(id);assertThat(actor.fullName()).isEqualTo("Kỹ thuật viên");assertThat(actor.has(RoleName.TECHNICIAN)).isTrue();}
    @Test void rejectsServiceJwtAsUser(){Jwt jwt=Jwt.withTokenValue("token").header("alg","none").subject("inventory-service")
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).claim("token_type","service")
            .claim("scope","internal").claim("roles",List.of()).build();SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        assertThatThrownBy(()->new CurrentUser().require()).isInstanceOf(ApiException.class);}
}
