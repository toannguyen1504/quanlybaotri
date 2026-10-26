package com.example.quanlybaotri.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.quanlybaotri.identity.domain.RegistrationSource;
import com.example.quanlybaotri.identity.domain.UserAccount;
import com.example.quanlybaotri.identity.persistence.RefreshTokenRepository;
import com.example.quanlybaotri.identity.persistence.UserRepository;
import com.example.quanlybaotri.shared.api.ApiException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    private final JwtService jwt = mock(JwtService.class);
    private final LoginRateLimiter limiter = mock(LoginRateLimiter.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, refreshTokens, jwt, limiter, encoder, 7);
    }

    @Test
    void loginAcceptsEmailAsTheIdentifier() {
        UserAccount user = new UserAccount("person", "person@example.test", "hash", "Person");
        when(users.findWithRolesByUsernameOrEmail("person@example.test")).thenReturn(Optional.of(user));
        when(encoder.matches("Password@123", "hash")).thenReturn(true);
        when(jwt.issue(user)).thenReturn(new JwtService.IssuedAccessToken(
            "access", Instant.now().plusSeconds(900)
        ));

        AuthService.TokenPair tokens = service.login("Person@Example.Test", "Password@123", "127.0.0.1");

        assertThat(tokens.accessToken()).isEqualTo("access");
        verify(limiter).success("Person@Example.Test", "127.0.0.1");
    }

    @Test
    void pendingStatusIsOnlyDisclosedAfterThePasswordMatches() {
        UserAccount user = new UserAccount("person", "person@example.test", "hash", "Person");
        user.prepareSelfRegistration(RegistrationSource.LOCAL, "Example", null);
        when(users.findWithRolesByUsernameOrEmail("person")).thenReturn(Optional.of(user));

        when(encoder.matches("wrong", "hash")).thenReturn(false);
        assertCode("INVALID_CREDENTIALS", () -> service.login("person", "wrong", "127.0.0.1"));

        when(encoder.matches("correct", "hash")).thenReturn(true);
        assertCode("ACCOUNT_PENDING", () -> service.login("person", "correct", "127.0.0.1"));
    }

    private void assertCode(String expected, Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOf(ApiException.class)
            .extracting("code")
            .isEqualTo(expected);
    }
}
