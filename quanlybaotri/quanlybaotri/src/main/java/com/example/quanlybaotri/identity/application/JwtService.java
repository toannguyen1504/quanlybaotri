package com.example.quanlybaotri.identity.application;

import com.example.quanlybaotri.identity.domain.UserAccount;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final Duration accessTtl;

    public JwtService(JwtEncoder encoder, @Value("${app.jwt.access-minutes:15}") long minutes) {
        this.encoder = encoder;
        this.accessTtl = Duration.ofMinutes(minutes);
    }

    public IssuedAccessToken issue(UserAccount user) {
        Instant now = Instant.now();
        Instant expires = now.plus(accessTtl);
        List<String> roles = user.getRoles().stream().map(r -> "ROLE_" + r.getName().name()).sorted().toList();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("quanlybaotri").subject(user.getUsername()).issuedAt(now)
                .expiresAt(expires).id(UUID.randomUUID().toString()).claim("uid", user.getId().toString())
                .claim("roles", roles).build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return new IssuedAccessToken(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(),
                expires);
    }

    public record IssuedAccessToken(String value, Instant expiresAt) {
    }
}
