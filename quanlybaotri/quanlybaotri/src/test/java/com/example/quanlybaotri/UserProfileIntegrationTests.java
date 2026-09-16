package com.example.quanlybaotri;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.quanlybaotri.identity.application.UserService;
import com.example.quanlybaotri.identity.domain.RoleName;
import com.example.quanlybaotri.identity.domain.UserAccount;
import com.example.quanlybaotri.identity.persistence.RoleRepository;
import com.example.quanlybaotri.identity.persistence.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserProfileIntegrationTests {
    @Autowired
    UserService service;
    @Autowired
    UserRepository users;
    @Autowired
    RoleRepository roles;
    @Autowired
    PasswordEncoder encoder;
    @Autowired
    EntityManager entityManager;

    @Test
    void updatesOnlyTheCurrentUsersEditableProfileFields() {
        UserAccount user = user("profile", "profile-before@example.test", "OldPass@123");

        UserAccount updated = service.updateCurrentProfile(user.getId(), "profile-after@example.test",
                "Nguyễn Văn Mới", "0901234567");

        assertThat(updated.getUsername()).isEqualTo(user.getUsername());
        assertThat(updated.getEmail()).isEqualTo("profile-after@example.test");
        assertThat(updated.getFullName()).isEqualTo("Nguyễn Văn Mới");
        assertThat(updated.getPhone()).isEqualTo("0901234567");
        assertThat(updated.getRoles()).extracting(role -> role.getName()).containsExactly(RoleName.REQUESTER);
    }

    @Test
    void rejectsAnEmailOwnedByAnotherUser() {
        UserAccount user = user("profile-owner", "owner@example.test", "OldPass@123");
        user("profile-other", "other@example.test", "OldPass@123");

        assertThatThrownBy(() -> service.updateCurrentProfile(user.getId(), "other@example.test", "Owner", null))
                .hasMessageContaining("Email đã tồn tại");
    }

    @Test
    void persistsPasswordChangeAndClearsTemporaryPasswordWarning() {
        UserAccount user = user("password", "password@example.test", "OldPass@123");

        service.changePassword(user.getId(), "OldPass@123", "NewPass@456");
        entityManager.flush();
        entityManager.clear();

        UserAccount reloaded = users.findById(user.getId()).orElseThrow();
        assertThat(reloaded.isMustChangePassword()).isFalse();
        assertThat(encoder.matches("NewPass@456", reloaded.getPassword())).isTrue();
    }

    @Test
    void rejectsAnIncorrectCurrentPassword() {
        UserAccount user = user("wrong-password", "wrong-password@example.test", "OldPass@123");

        assertThatThrownBy(() -> service.changePassword(user.getId(), "NotThePassword", "NewPass@456"))
                .hasMessageContaining("Mật khẩu hiện tại không đúng");
        assertThat(encoder.matches("OldPass@123", users.findById(user.getId()).orElseThrow().getPassword())).isTrue();
    }

    private UserAccount user(String prefix, String email, String password) {
        String username = prefix + Long.toUnsignedString(System.nanoTime());
        UserAccount user = new UserAccount(username, email, encoder.encode(password), "Người dùng kiểm thử");
        user.setRoles(Set.of(roles.findByName(RoleName.REQUESTER).orElseThrow()));
        return users.save(user);
    }
}
