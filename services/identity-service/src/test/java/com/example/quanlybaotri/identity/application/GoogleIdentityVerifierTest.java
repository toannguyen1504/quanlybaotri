package com.example.quanlybaotri.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.quanlybaotri.shared.api.ApiException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class GoogleIdentityVerifierTest {
    @Test
    void rejectsWrongIssuerAndAudience() {
        Jwt wrongIssuer = token("https://issuer.example", List.of("client-id"), true);
        Jwt wrongAudience = token("https://accounts.google.com", List.of("another-client"), true);

        assertThat(GoogleIdentityVerifier.validatorFor("client-id").validate(wrongIssuer).hasErrors()).isTrue();
        assertThat(GoogleIdentityVerifier.validatorFor("client-id").validate(wrongAudience).hasErrors()).isTrue();
    }

    @Test
    void rejectsAnInvalidSignatureReportedByTheJwtDecoder() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        when(decoder.decode("credential")).thenThrow(new JwtException("invalid signature"));
        GoogleIdentityVerifier verifier = new GoogleIdentityVerifier("client-id", decoder);

        assertInvalid(() -> verifier.verify("credential"));
    }

    @Test
    void requiresGoogleToMarkTheEmailAsVerified() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        when(decoder.decode("credential")).thenReturn(
            token("https://accounts.google.com", List.of("client-id"), false)
        );
        GoogleIdentityVerifier verifier = new GoogleIdentityVerifier("client-id", decoder);

        assertInvalid(() -> verifier.verify("credential"));
    }

    private Jwt token(String issuer, List<String> audience, boolean verified) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("google-subject")
            .issuer(issuer)
            .audience(audience)
            .issuedAt(now.minusSeconds(10))
            .expiresAt(now.plusSeconds(300))
            .claim("email", "person@example.test")
            .claim("email_verified", verified)
            .claim("name", "Person")
            .build();
    }

    private void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOf(ApiException.class)
            .extracting("code")
            .isEqualTo("INVALID_GOOGLE_CREDENTIAL");
    }
}
