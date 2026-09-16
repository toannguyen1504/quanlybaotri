package com.example.quanlybaotri.identity.config;

import com.example.quanlybaotri.identity.domain.RoleName;
import com.example.quanlybaotri.identity.domain.UserAccount;
import com.example.quanlybaotri.identity.persistence.RoleRepository;
import com.example.quanlybaotri.identity.persistence.UserRepository;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    @Value("${app.bootstrap.admin.username:}")
    private String username;
    @Value("${app.bootstrap.admin.password:}")
    private String password;
    @Value("${app.bootstrap.admin.email:}")
    private String email;

    public AdminBootstrap(UserRepository users, RoleRepository roles, PasswordEncoder encoder) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (username == null || username.isBlank() || users.existsByUsernameIgnoreCase(username))
            return;
        if (password == null || password.length() < 8)
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD must contain at least 8 characters");
        UserAccount admin = new UserAccount(username, email, encoder.encode(password), "System Administrator");
        admin.setRoles(Set.of(roles.findByName(RoleName.ADMIN).orElseThrow()));
        users.save(admin);
    }
}
