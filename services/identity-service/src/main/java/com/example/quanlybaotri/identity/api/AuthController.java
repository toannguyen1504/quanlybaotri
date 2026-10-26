package com.example.quanlybaotri.identity.api;

import com.example.quanlybaotri.identity.application.AuthService;
import com.example.quanlybaotri.identity.application.RegistrationService;
import com.example.quanlybaotri.identity.application.TokenStore;
import com.example.quanlybaotri.identity.domain.UserAccount;
import com.example.quanlybaotri.shared.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;
    private final CurrentUser currentUser;
    private final TokenStore tokenStore;
    private final RegistrationService registrations;

    public AuthController(
        AuthService auth,
        RegistrationService registrations,
        CurrentUser currentUser,
        TokenStore tokenStore
    ) {
        this.auth = auth;
        this.registrations = registrations;
        this.currentUser = currentUser;
        this.tokenStore = tokenStore;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RegistrationService.RegistrationResult register(
        @Valid @RequestBody RegisterRequest request,
        HttpServletRequest http
    ) {
        return registrations.register(new RegistrationService.RegisterCommand(
            request.email(), request.password(), request.fullName(), request.phone(),
            request.organizationName(), request.note()
        ), clientIp(http));
    }

    @PostMapping("/google")
    public RegistrationService.GoogleAuthResult google(
        @Valid @RequestBody GoogleRequest request,
        HttpServletRequest http
    ) {
        return registrations.google(new RegistrationService.GoogleCommand(
            request.credential(), request.mode(), request.phone(),
            request.organizationName(), request.note()
        ), clientIp(http));
    }

    @GetMapping("/config")
    public RegistrationService.GoogleConfig config() {
        return registrations.config();
    }

    @PostMapping("/login")
    public AuthService.TokenPair login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest http
    ) {
        return auth.login(request.username(), request.password(), clientIp(http));
    }

    @PostMapping("/refresh")
    public AuthService.TokenPair refresh(@Valid @RequestBody RefreshRequest request) {
        return auth.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        @RequestBody(required = false) RefreshRequest request,
        JwtAuthenticationToken principal
    ) {
        if (request != null) auth.logout(request.refreshToken());
        Instant expires = principal.getToken().getExpiresAt();
        if (expires != null) tokenStore.revoke(
            principal.getToken().getId(),
            Duration.between(Instant.now(), expires)
        );
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public MeResponse me() {
        UserAccount u = currentUser.require();
        return MeResponse.from(u);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record MeResponse(
        java.util.UUID id,
        String username,
        String email,
        String fullName,
        String phone,
        com.example.quanlybaotri.identity.domain.AccountType accountType,
        java.util.UUID customerId,
        com.example.quanlybaotri.identity.domain.SiteScope siteScope,
        Set<java.util.UUID> siteIds,
        com.example.quanlybaotri.identity.domain.RegionScope regionScope,
        Set<java.util.UUID> regionIds,
        boolean enabled,
        boolean mustChangePassword,
        boolean hasPassword,
        boolean googleLinked,
        com.example.quanlybaotri.identity.domain.RegistrationStatus registrationStatus,
        com.example.quanlybaotri.identity.domain.RegistrationSource registrationSource,
        String requestedOrganizationName,
        String registrationNote,
        String rejectionReason,
        Instant registeredAt,
        Instant reviewedAt,
        Set<String> roles
    ) {
        public static MeResponse from(UserAccount u) {
            return new MeResponse(
                u.getId(),
                u.getUsername(),
                u.getEmail(),
                u.getFullName(),
                u.getPhone(),
                u.getAccountType(),
                u.getCustomerId(),
                u.getSiteScope(),
                u.getSiteIds(),
                u.getRegionScope(),
                u.getRegionIds(),
                u.isEnabled(),
                u.isMustChangePassword(),
                u.hasPassword(),
                u.isGoogleLinked(),
                u.getRegistrationStatus(),
                u.getRegistrationSource(),
                u.getRequestedOrganizationName(),
                u.getRegistrationNote(),
                u.getRejectionReason(),
                u.getRegisteredAt(),
                u.getReviewedAt(),
                u
                    .getRoles()
                    .stream()
                    .map(r -> r.getName().name())
                    .collect(java.util.stream.Collectors.toSet())
            );
        }
    }

    public record RegisterRequest(
        @NotBlank @Email @Size(max = 190) String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 150) String fullName,
        @Size(max = 30) String phone,
        @NotBlank @Size(max = 190) String organizationName,
        @Size(max = 1000) String note
    ) {}

    public record GoogleRequest(
        @NotBlank String credential,
        @jakarta.validation.constraints.NotNull RegistrationService.GoogleMode mode,
        @Size(max = 30) String phone,
        @Size(max = 190) String organizationName,
        @Size(max = 1000) String note
    ) {}
}
