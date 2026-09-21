package com.example.quanlybaotri.identity.api;

import com.example.quanlybaotri.identity.application.JwtService;
import com.example.quanlybaotri.shared.api.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/auth")
public class ServiceTokenController {

    private final JwtService jwt;
    private final Map<String, String> secrets;
    private final Map<String, Set<String>> grants;

    public ServiceTokenController(
        JwtService jwt,
        @Value("${app.service-clients.identity-secret}") String identity,
        @Value("${app.service-clients.asset-secret}") String asset,
        @Value("${app.service-clients.maintenance-secret}") String maintenance,
        @Value("${app.service-clients.inventory-secret}") String inventory,
        @Value("${app.service-clients.notification-secret}") String notification
    ) {
        this.jwt = jwt;
        secrets = Map.of(
            "identity-service",
            identity,
            "asset-service",
            asset,
            "maintenance-service",
            maintenance,
            "inventory-service",
            inventory,
            "notification-service",
            notification
        );
        grants = Map.of(
            "identity-service",
            Set.of("organization-service"),
            "asset-service",
            Set.of("organization-service"),
            "maintenance-service",
            Set.of("identity-service", "asset-service"),
            "inventory-service",
            Set.of("identity-service", "maintenance-service"),
            "notification-service",
            Set.of("identity-service")
        );
    }

    @PostMapping("/token")
    public TokenResponse token(
        HttpServletRequest request,
        @RequestParam(defaultValue = "client_credentials") String grant_type,
        @RequestParam String audience
    ) {
        if (!"client_credentials".equals(grant_type)) throw new ApiException(
            HttpStatus.BAD_REQUEST,
            "UNSUPPORTED_GRANT",
            "Chỉ hỗ trợ client_credentials"
        );
        String[] credentials = basic(request);
        String expected = secrets.get(credentials[0]);
        if (
            expected == null ||
            !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                credentials[1].getBytes(StandardCharsets.UTF_8)
            ) ||
            !grants.getOrDefault(credentials[0], Set.of()).contains(audience)
        ) throw new ApiException(
            HttpStatus.UNAUTHORIZED,
            "INVALID_CLIENT",
            "Service client không hợp lệ"
        );
        var issued = jwt.issueService(credentials[0], audience);
        return new TokenResponse(
            issued.value(),
            "Bearer",
            Duration.between(Instant.now(), issued.expiresAt()).toSeconds()
        );
    }

    private String[] basic(HttpServletRequest r) {
        String value = r.getHeader("Authorization");
        try {
            if (value == null || !value.startsWith("Basic ")) throw new IllegalArgumentException();
            String decoded = new String(
                Base64.getDecoder().decode(value.substring(6)),
                StandardCharsets.UTF_8
            );
            int split = decoded.indexOf(':');
            if (split < 1) throw new IllegalArgumentException();
            return new String[] { decoded.substring(0, split), decoded.substring(split + 1) };
        } catch (Exception e) {
            throw new ApiException(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CLIENT",
                "Thiếu client credentials"
            );
        }
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}
}
