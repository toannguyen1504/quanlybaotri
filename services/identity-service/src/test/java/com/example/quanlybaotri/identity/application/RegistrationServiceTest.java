package com.example.quanlybaotri.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.quanlybaotri.identity.domain.RegistrationSource;
import com.example.quanlybaotri.identity.domain.RegistrationStatus;
import com.example.quanlybaotri.identity.domain.UserAccount;
import com.example.quanlybaotri.identity.persistence.UserRepository;
import com.example.quanlybaotri.shared.api.ApiException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class RegistrationServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final AuthService auth = mock(AuthService.class);
    private final GoogleIdentityVerifier google = mock(GoogleIdentityVerifier.class);
    private final LoginRateLimiter limiter = mock(LoginRateLimiter.class);
    private RegistrationService service;

    @BeforeEach
    void setUp() {
        service = new RegistrationService(users, encoder, auth, google, limiter);
        when(users.saveAndFlush(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void localRegistrationCreatesInactiveCustomerPendingReviewWithGeneratedUsername() {
        when(encoder.encode("Password@123")).thenReturn("encoded");
        when(users.existsByEmailIgnoreCase("person@example.test")).thenReturn(false);
        when(users.existsByUsernameIgnoreCase("person")).thenReturn(false);

        var result = service.register(new RegistrationService.RegisterCommand(
            "Person@Example.Test", "Password@123", "Nguyen Van A", "0901",
            "Example Company", "Site Hanoi"
        ), "127.0.0.1");

        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(users).saveAndFlush(saved.capture());
        UserAccount user = saved.getValue();
        assertThat(result.status()).isEqualTo(RegistrationService.AuthOutcome.PENDING);
        assertThat(user.getUsername()).isEqualTo("person");
        assertThat(user.getRegistrationStatus()).isEqualTo(RegistrationStatus.PENDING);
        assertThat(user.getRegistrationSource()).isEqualTo(RegistrationSource.LOCAL);
        assertThat(user.isEnabled()).isFalse();
        assertThat(user.getRoles()).isEmpty();
        assertThat(user.getRequestedOrganizationName()).isEqualTo("Example Company");
    }

    @Test
    void verifiedGoogleEmailAutomaticallyLinksAnExistingApprovedAccount() {
        UserAccount existing = new UserAccount("person", "person@example.test", "hash", "Person");
        when(google.verify("credential")).thenReturn(
            new GoogleIdentityVerifier.GoogleProfile("google-subject", "person@example.test", "Person")
        );
        when(users.findWithRolesByGoogleSubject("google-subject")).thenReturn(Optional.empty());
        when(users.findWithRolesByUsernameOrEmail("person@example.test")).thenReturn(Optional.of(existing));
        when(auth.issuePair(existing)).thenReturn(new AuthService.TokenPair(
            "access", "refresh", Instant.now().plusSeconds(60), Instant.now().plusSeconds(120)
        ));

        var result = service.google(new RegistrationService.GoogleCommand(
            "credential", RegistrationService.GoogleMode.LOGIN, null, null, null
        ), "127.0.0.1");

        assertThat(result.status()).isEqualTo(RegistrationService.AuthOutcome.AUTHENTICATED);
        assertThat(existing.getGoogleSubject()).isEqualTo("google-subject");
        assertThat(result.tokens().accessToken()).isEqualTo("access");
    }

    @Test
    void googleLoginDoesNotCreateAnUnknownAccount() {
        when(google.verify("credential")).thenReturn(
            new GoogleIdentityVerifier.GoogleProfile("new-subject", "new@example.test", "New User")
        );
        when(users.findWithRolesByGoogleSubject("new-subject")).thenReturn(Optional.empty());
        when(users.findWithRolesByUsernameOrEmail("new@example.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.google(new RegistrationService.GoogleCommand(
            "credential", RegistrationService.GoogleMode.LOGIN, null, null, null
        ), "127.0.0.1"))
            .isInstanceOf(ApiException.class)
            .extracting("code")
            .isEqualTo("GOOGLE_ACCOUNT_NOT_REGISTERED");
    }
}
