package com.example.quanlybaotri.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;

class JwtDecoderAudienceTest {

    @Test
    @SuppressWarnings("unchecked")
    void should_reject_service_token_for_another_audience() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.hasKey(anyString())).thenReturn(false);
        NimbusJwtDecoder decoder = (NimbusJwtDecoder) new JwtDecoderConfig()
            .jwtDecoder("https://identity.test/.well-known/jwks.json", "asset-service", redis);
        OAuth2TokenValidator<Jwt> validator =
            (OAuth2TokenValidator<Jwt>) ReflectionTestUtils.getField(decoder, "jwtValidator");

        assertThat(validator.validate(serviceJwt("another-service")).hasErrors()).isTrue();
        assertThat(validator.validate(serviceJwt("asset-service")).hasErrors()).isFalse();
    }

    private Jwt serviceJwt(String audience) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("service-token")
            .header("alg", "RS256")
            .issuer("quanlybaotri")
            .subject("calling-service")
            .audience(List.of(audience))
            .claim("token_type", "service")
            .claim("scope", "internal")
            .jti("jwt-id")
            .issuedAt(now.minusSeconds(5))
            .expiresAt(now.plusSeconds(60))
            .build();
    }
}
