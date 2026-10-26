package com.example.quanlybaotri.identity.application;

import com.example.quanlybaotri.shared.api.ApiException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

@Component
public class GoogleIdentityVerifier {
    private static final String GOOGLE_JWKS = "https://www.googleapis.com/oauth2/v3/certs";
    private final String clientId;
    private final JwtDecoder decoder;

    @org.springframework.beans.factory.annotation.Autowired
    public GoogleIdentityVerifier(@Value("${app.google.client-id:}") String clientId) {
        this.clientId = clientId == null ? "" : clientId.trim();
        NimbusJwtDecoder google = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS).build();
        google.setJwtValidator(validatorFor(this.clientId));
        decoder = google;
    }

    GoogleIdentityVerifier(String clientId, JwtDecoder decoder) {
        this.clientId = clientId == null ? "" : clientId.trim();
        this.decoder = decoder;
    }

    static OAuth2TokenValidator<Jwt> validatorFor(String clientId) {
        OAuth2TokenValidator<Jwt> issuer = token -> List.of(
            "accounts.google.com", "https://accounts.google.com"
        ).contains(token.getIssuer() == null ? "" : token.getIssuer().toString())
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_issuer"));
        OAuth2TokenValidator<Jwt> audience = token -> clientId != null && !clientId.isBlank() &&
            token.getAudience().contains(clientId)
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_audience"));
        return new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(), issuer, audience
        );
    }

    public GoogleProfile verify(String credential) {
        if (!enabled()) throw new ApiException(
            HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_DISABLED", "Đăng nhập Google chưa được cấu hình"
        );
        try {
            Jwt token = decoder.decode(credential);
            String subject = token.getSubject();
            String email = token.getClaimAsString("email");
            Boolean verified = token.getClaim("email_verified");
            String name = token.getClaimAsString("name");
            if (subject == null || subject.isBlank() || email == null || email.isBlank() || !Boolean.TRUE.equals(verified)) {
                throw invalid();
            }
            return new GoogleProfile(subject, email.trim().toLowerCase(), name == null ? email : name.trim());
        } catch (JwtException exception) {
            throw invalid();
        }
    }

    public boolean enabled() { return !clientId.isBlank(); }
    public String clientId() { return clientId; }

    private ApiException invalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_GOOGLE_CREDENTIAL", "Thông tin xác thực Google không hợp lệ");
    }

    public record GoogleProfile(String subject, String email, String fullName) {}
}
