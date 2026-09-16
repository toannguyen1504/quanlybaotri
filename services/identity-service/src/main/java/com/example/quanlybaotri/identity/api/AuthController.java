package com.example.quanlybaotri.identity.api;

import com.example.quanlybaotri.identity.application.AuthService;
import com.example.quanlybaotri.identity.application.TokenStore;
import com.example.quanlybaotri.identity.domain.UserAccount;
import com.example.quanlybaotri.shared.security.CurrentUser;
import com.example.quanlybaotri.organization.client.OrganizationClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.springframework.http.ResponseEntity;
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
    private final OrganizationClient organizations;

    public AuthController(AuthService auth, CurrentUser currentUser, TokenStore tokenStore,OrganizationClient organizations) {
        this.auth = auth;
        this.currentUser = currentUser;
        this.tokenStore = tokenStore;
        this.organizations=organizations;
    }

    @PostMapping("/login")
    public AuthService.TokenPair login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return auth.login(request.username(), request.password(), clientIp(http));
    }

    @PostMapping("/refresh")
    public AuthService.TokenPair refresh(@Valid @RequestBody RefreshRequest request) {
        return auth.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest request,
            JwtAuthenticationToken principal) {
        if (request != null)
            auth.logout(request.refreshToken());
        Instant expires = principal.getToken().getExpiresAt();
        if (expires != null)
            tokenStore.revoke(principal.getToken().getId(), Duration.between(Instant.now(), expires));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public MeResponse me() {
        UserAccount u = currentUser.require();
        return MeResponse.from(u,u.getDepartmentId()==null?null:organizations.find(u.getDepartmentId()));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record MeResponse(java.util.UUID id, String username, String email, String fullName, String phone,
            java.util.UUID departmentId, String departmentName, boolean enabled, boolean mustChangePassword,
            Set<String> roles) {
        public static MeResponse from(UserAccount u,OrganizationClient.DepartmentRef department) {
            return new MeResponse(u.getId(), u.getUsername(), u.getEmail(), u.getFullName(), u.getPhone(),
                    u.getDepartmentId(),department==null?null:department.name(), u.isEnabled(),
                    u.isMustChangePassword(),
                    u.getRoles().stream().map(r -> r.getName().name()).collect(java.util.stream.Collectors.toSet()));
        }
    }
}
