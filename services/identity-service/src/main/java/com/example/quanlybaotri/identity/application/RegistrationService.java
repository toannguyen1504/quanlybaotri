package com.example.quanlybaotri.identity.application;

import com.example.quanlybaotri.identity.domain.*;
import com.example.quanlybaotri.identity.persistence.UserRepository;
import com.example.quanlybaotri.shared.api.ApiException;
import java.security.SecureRandom;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {
    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuthService auth;
    private final GoogleIdentityVerifier google;
    private final LoginRateLimiter limiter;
    private final SecureRandom random = new SecureRandom();

    public RegistrationService(
        UserRepository users,
        PasswordEncoder encoder,
        AuthService auth,
        GoogleIdentityVerifier google,
        LoginRateLimiter limiter
    ) {
        this.users = users;
        this.encoder = encoder;
        this.auth = auth;
        this.google = google;
        this.limiter = limiter;
    }

    @Transactional
    public RegistrationResult register(RegisterCommand command, String ip) {
        String email = normalizeEmail(command.email());
        if (limiter.registerBlocked(email, ip)) throw rateLimited();
        if (users.existsByEmailIgnoreCase(email)) throw ApiException.conflict("Email đã tồn tại");
        UserAccount user = new UserAccount(
            uniqueUsername(email), email, encoder.encode(command.password()), command.fullName().trim()
        );
        user.updateProfile(email, command.fullName().trim(), clean(command.phone()));
        user.prepareSelfRegistration(RegistrationSource.LOCAL, command.organizationName(), command.note());
        save(user);
        return pending(email);
    }

    @Transactional
    public GoogleAuthResult google(GoogleCommand command, String ip) {
        if (limiter.googleIpBlocked(ip)) throw rateLimited();
        GoogleIdentityVerifier.GoogleProfile profile = google.verify(command.credential());
        if (limiter.googleEmailBlocked(profile.email())) throw rateLimited();
        UserAccount user = users.findWithRolesByGoogleSubject(profile.subject()).orElse(null);
        if (user == null) {
            user = users.findWithRolesByUsernameOrEmail(profile.email()).orElse(null);
            if (user != null) {
                try {
                    user.linkGoogle(profile.subject());
                } catch (IllegalStateException exception) {
                    throw ApiException.conflict("Email đã liên kết với tài khoản Google khác");
                }
            }
        }
        if (user == null) {
            if (command.mode() != GoogleMode.REGISTER) throw new ApiException(
                HttpStatus.FORBIDDEN, "GOOGLE_ACCOUNT_NOT_REGISTERED", "Tài khoản Google chưa được đăng ký"
            );
            requireRegistrationContext(command.organizationName());
            user = new UserAccount(uniqueUsername(profile.email()), profile.email(), null, profile.fullName());
            user.linkGoogle(profile.subject());
            user.updateProfile(profile.email(), profile.fullName(), clean(command.phone()));
            user.prepareSelfRegistration(RegistrationSource.GOOGLE, command.organizationName(), command.note());
            save(user);
        } else if (command.mode() == GoogleMode.REGISTER && user.getRegistrationStatus() == RegistrationStatus.PENDING) {
            requireRegistrationContext(command.organizationName());
            user.updateRegistrationRequest(command.phone(), command.organizationName(), command.note());
        }
        return outcome(user);
    }

    public GoogleConfig config() {
        return new GoogleConfig(google.enabled(), google.enabled() ? google.clientId() : null, true);
    }

    private GoogleAuthResult outcome(UserAccount user) {
        if (user.getRegistrationStatus() == RegistrationStatus.PENDING) {
            return new GoogleAuthResult(AuthOutcome.PENDING, null, "Tài khoản đang chờ quản trị viên duyệt", null);
        }
        if (user.getRegistrationStatus() == RegistrationStatus.REJECTED) {
            return new GoogleAuthResult(AuthOutcome.REJECTED, null, "Yêu cầu đăng ký đã bị từ chối", user.getRejectionReason());
        }
        if (!user.isEnabled()) throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "Tài khoản đã bị khóa");
        return new GoogleAuthResult(AuthOutcome.AUTHENTICATED, auth.issuePair(user), null, null);
    }

    private String uniqueUsername(String email) {
        String base = email.substring(0, email.indexOf('@')).toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9._-]", "").replaceAll("^[._-]+|[._-]+$", "");
        if (base.isBlank()) base = "user";
        if (base.length() > 70) base = base.substring(0, 70);
        if (!users.existsByUsernameIgnoreCase(base)) return base;
        String candidate;
        do candidate = base + "-" + suffix(); while (users.existsByUsernameIgnoreCase(candidate));
        return candidate;
    }

    private String suffix() {
        StringBuilder value = new StringBuilder(6);
        for (int i = 0; i < 6; i++) value.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return value.toString();
    }

    private void save(UserAccount user) {
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw ApiException.conflict("Email hoặc tài khoản Google đã tồn tại");
        }
    }

    private void requireRegistrationContext(String organizationName) {
        if (organizationName == null || organizationName.isBlank()) throw new ApiException(
            HttpStatus.BAD_REQUEST, "ORGANIZATION_REQUIRED", "Vui lòng nhập tên đơn vị hoặc công ty"
        );
    }

    private String normalizeEmail(String value) { return value.trim().toLowerCase(Locale.ROOT); }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private RegistrationResult pending(String email) {
        return new RegistrationResult(AuthOutcome.PENDING, email, "Đăng ký thành công, vui lòng chờ quản trị viên duyệt");
    }
    private ApiException rateLimited() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "AUTH_RATE_LIMIT", "Thử lại sau 15 phút");
    }

    public enum GoogleMode { LOGIN, REGISTER }
    public enum AuthOutcome { AUTHENTICATED, PENDING, REJECTED }
    public record RegisterCommand(String email, String password, String fullName, String phone, String organizationName, String note) {}
    public record GoogleCommand(String credential, GoogleMode mode, String phone, String organizationName, String note) {}
    public record RegistrationResult(AuthOutcome status, String email, String message) {}
    public record GoogleAuthResult(AuthOutcome status, AuthService.TokenPair tokens, String message, String rejectionReason) {}
    public record GoogleConfig(boolean googleEnabled, String googleClientId, boolean registrationEnabled) {}
}
