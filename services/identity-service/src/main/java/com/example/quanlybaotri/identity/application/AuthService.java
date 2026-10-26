package com.example.quanlybaotri.identity.application;

import com.example.quanlybaotri.identity.domain.RefreshToken;
import com.example.quanlybaotri.identity.domain.UserAccount;
import com.example.quanlybaotri.identity.domain.RegistrationStatus;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final JwtService jwt;
    private final LoginRateLimiter limiter;
    private final PasswordEncoder encoder;
    private final Duration refreshTtl;
    private final SecureRandom random = new SecureRandom();

    public AuthService(
        UserRepository users,
        RefreshTokenRepository refreshTokens,
        JwtService jwt,
        LoginRateLimiter limiter,
        PasswordEncoder encoder,
        @Value("${app.jwt.refresh-days:7}") long refreshDays
    ) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.jwt = jwt;
        this.limiter = limiter;
        this.encoder = encoder;
        this.refreshTtl = Duration.ofDays(refreshDays);
    }

    @Transactional
    public TokenPair login(String username, String password, String ip) {
        if (limiter.blocked(username, ip)) throw new ApiException(
            HttpStatus.TOO_MANY_REQUESTS,
            "LOGIN_RATE_LIMIT",
            "Thử đăng nhập lại sau 15 phút"
        );
        UserAccount user = users.findWithRolesByUsernameOrEmail(username.trim().toLowerCase()).orElse(null);
        if (user == null || !user.hasPassword() || !encoder.matches(password, user.getPassword())) {
            log.warn("Login authentication failed for identifier {}", username.toLowerCase());
            limiter.failure(username, ip);
            throw new ApiException(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "Tên đăng nhập hoặc mật khẩu không đúng"
            );
        }
        limiter.success(username, ip);
        ensureLoginAllowed(user);
        return issuePair(user, null);
    }

    @Transactional
    public TokenPair refresh(String rawToken) {
        RefreshToken current = refreshTokens
            .findByTokenHash(hash(rawToken))
            .orElseThrow(() ->
                new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_REFRESH_TOKEN",
                    "Refresh token không hợp lệ"
                )
            );
        if (!current.usable()) throw new ApiException(
            HttpStatus.UNAUTHORIZED,
            "EXPIRED_REFRESH_TOKEN",
            "Refresh token đã hết hạn hoặc bị thu hồi"
        );
        ensureLoginAllowed(current.getUser());
        return issuePair(current.getUser(), current);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) return;
        refreshTokens
            .findByTokenHash(hash(rawRefreshToken))
            .filter(RefreshToken::usable)
            .ifPresent(t -> t.revoke(null));
    }

    @Transactional
    public TokenPair issuePair(UserAccount user) {
        return issuePair(user, null);
    }

    private TokenPair issuePair(UserAccount user, RefreshToken replaced) {
        String raw = randomToken();
        String hash = hash(raw);
        if (replaced != null) replaced.revoke(hash);
        RefreshToken refresh = new RefreshToken(user, hash, Instant.now().plus(refreshTtl));
        refreshTokens.save(refresh);
        JwtService.IssuedAccessToken access = jwt.issue(user);
        return new TokenPair(access.value(), raw, access.expiresAt(), refresh.getExpiresAt());
    }

    private void ensureLoginAllowed(UserAccount user) {
        if (user.getRegistrationStatus() == RegistrationStatus.PENDING) throw new ApiException(
            HttpStatus.FORBIDDEN, "ACCOUNT_PENDING", "Tài khoản đang chờ quản trị viên duyệt"
        );
        if (user.getRegistrationStatus() == RegistrationStatus.REJECTED) throw new ApiException(
            HttpStatus.FORBIDDEN, "ACCOUNT_REJECTED",
            user.getRejectionReason() == null ? "Yêu cầu đăng ký đã bị từ chối" : user.getRejectionReason()
        );
        if (!user.isEnabled()) throw new ApiException(
            HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "Tài khoản đã bị khóa"
        );
    }

    private String randomToken() {
        byte[] bytes = new byte[48];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public record TokenPair(
        String accessToken,
        String refreshToken,
        Instant accessExpiresAt,
        Instant refreshExpiresAt
    ) {}
}
