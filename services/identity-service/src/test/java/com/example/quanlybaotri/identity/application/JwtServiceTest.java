package com.example.quanlybaotri.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtServiceTest {

    @Test
    void serviceTokenHasInternalScopeTargetAudienceAndFiveMinuteLifetime() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var publicKey = (RSAPublicKey) pair.getPublic();
        var rsa = new RSAKey.Builder(publicKey)
            .privateKey((RSAPrivateKey) pair.getPrivate())
            .build();
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(rsa)));
        var service = new JwtService(encoder, 15);

        var issued = service.issueService("maintenance-service", "asset-service");
        var token = NimbusJwtDecoder.withPublicKey(publicKey).build().decode(issued.value());

        assertEquals("maintenance-service", token.getSubject());
        assertEquals("service", token.getClaimAsString("token_type"));
        assertEquals("internal", token.getClaimAsString("scope"));
        assertEquals(java.util.List.of("asset-service"), token.getAudience());
        long lifetime = Duration.between(token.getIssuedAt(), token.getExpiresAt()).toSeconds();
        assertTrue(lifetime >= 299 && lifetime <= 300);
    }
}
