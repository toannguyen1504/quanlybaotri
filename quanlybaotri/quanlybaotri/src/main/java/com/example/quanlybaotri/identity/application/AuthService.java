package com.example.quanlybaotri.identity.application;

import com.example.quanlybaotri.identity.domain.RefreshToken;
import com.example.quanlybaotri.identity.domain.UserAccount;
import com.example.quanlybaotri.identity.persistence.RefreshTokenRepository;
import com.example.quanlybaotri.identity.persistence.UserRepository;
import com.example.quanlybaotri.shared.api.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final JwtService jwt;
    private final LoginRateLimiter limiter;
    private final Duration refreshTtl;
    private final SecureRandom random = new SecureRandom();

    public AuthService(AuthenticationManager authenticationManager, UserRepository users,
            RefreshTokenRepository refreshTokens, JwtService jwt, LoginRateLimiter limiter,
            @Value("${app.jwt.refresh-days:7}") long refreshDays) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.jwt = jwt;
        this.limiter = limiter;
        this.refreshTtl = Duration.ofDays(refreshDays);
    }

    @Transactional
    public TokenPair login(String username, String password, String ip) {
        if (limiter.blocked(username, ip))
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_RATE_LIMIT", "Thử đăng nhập lại sau 15 phút");
        try {
            authenticationManager
                    .authenticate(new UsernamePasswordAuthenticationToken(username.toLowerCase(), password));
        } catch (BadCredentialsException ex) {
            limiter.failure(username, ip);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                    "Tên đăng nhập hoặc mật khẩu không đúng");
        }
        limiter.success(username, ip);
        UserAccount user = users.findWithRolesByUsername(username.toLowerCase()).orElseThrow();
        return issuePair(user, null);
    }

    @Transactional
    public TokenPair refresh(String rawToken) {
        RefreshToken current = refreshTokens.findByTokenHash(hash(rawToken)).orElseThrow(
                () -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token không hợp lệ"));
        if (!current.usable())
            throw new ApiException(HttpStatus.UNAUTHORIZED, "EXPIRED_REFRESH_TOKEN",
                    "Refresh token đã hết hạn hoặc bị thu hồi");
        return issuePair(current.getUser(), current);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank())
            return;
        refreshTokens.findByTokenHash(hash(rawRefreshToken)).filter(RefreshToken::usable)
                .ifPresent(t -> t.revoke(null));
    }

    private TokenPair issuePair(UserAccount user, RefreshToken replaced) {
        String raw = randomToken();
        String hash = hash(raw);
        if (replaced != null)
            replaced.revoke(hash);
        RefreshToken refresh = new RefreshToken(user, hash, Instant.now().plus(refreshTtl));
        refreshTokens.save(refresh);
        JwtService.IssuedAccessToken access = jwt.issue(user);
        return new TokenPair(access.value(), raw, access.expiresAt(), refresh.getExpiresAt());
    }

    private String randomToken() {
        byte[] bytes = new byte[48];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public record TokenPair(String accessToken, String refreshToken, Instant accessExpiresAt,
            Instant refreshExpiresAt) {
    }
}
