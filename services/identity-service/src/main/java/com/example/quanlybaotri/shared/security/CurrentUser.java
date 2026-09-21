package com.example.quanlybaotri.shared.security;

import com.example.quanlybaotri.identity.domain.UserAccount;
import com.example.quanlybaotri.identity.persistence.UserRepository;
import com.example.quanlybaotri.shared.api.ApiException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public UserAccount require() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return users
            .findWithRolesByUsername(username)
            .orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản hiện tại"));
    }
}
