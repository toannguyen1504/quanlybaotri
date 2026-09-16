package com.example.quanlybaotri.identity.application;

import com.example.quanlybaotri.identity.api.UserController.UserRequest;
import com.example.quanlybaotri.identity.api.UserController.UserView;
import com.example.quanlybaotri.identity.domain.Role;
import com.example.quanlybaotri.identity.domain.RoleName;
import com.example.quanlybaotri.identity.domain.UserAccount;
import com.example.quanlybaotri.identity.persistence.RoleRepository;
import com.example.quanlybaotri.identity.persistence.UserRepository;
import com.example.quanlybaotri.organization.domain.Department;
import com.example.quanlybaotri.organization.persistence.DepartmentRepository;
import com.example.quanlybaotri.shared.api.ApiException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final DepartmentRepository departments;
    private final PasswordEncoder encoder;

    public UserService(UserRepository users, RoleRepository roles, DepartmentRepository departments,
            PasswordEncoder encoder) {
        this.users = users;
        this.roles = roles;
        this.departments = departments;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public Page<UserView> list(Pageable pageable) {
        return users.findAll(pageable).map(UserView::from);
    }

    @Transactional
    public UserView create(UserRequest request) {
        if (request.password() == null || request.password().length() < 8)
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Mật khẩu phải có ít nhất 8 ký tự");
        if (users.existsByUsernameIgnoreCase(request.username()))
            throw ApiException.conflict("Tên đăng nhập đã tồn tại");
        if (users.existsByEmailIgnoreCase(request.email()))
            throw ApiException.conflict("Email đã tồn tại");
        UserAccount user = new UserAccount(request.username(), request.email(), encoder.encode(request.password()),
                request.fullName());
        apply(user, request);
        return UserView.from(users.save(user));
    }

    @Transactional
    public UserView update(UUID id, UserRequest request) {
        UserAccount user = users.findWithRolesById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));
        apply(user, request);
        user.setEnabled(request.enabled());
        return UserView.from(user);
    }

    @Transactional
    public void resetPassword(UUID id, String password) {
        UserAccount user = users.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));
        user.changePassword(encoder.encode(password), true);
    }

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        UserAccount user = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));
        if (!encoder.matches(currentPassword, user.getPassword()))
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_PASSWORD",
                    "Mật khẩu hiện tại không đúng");
        user.changePassword(encoder.encode(newPassword), false);
    }

    @Transactional
    public UserAccount updateCurrentProfile(UUID userId, String email, String fullName, String phone) {
        UserAccount user = users.findWithRolesById(userId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));
        if (users.existsByEmailIgnoreCaseAndIdNot(email, userId))
            throw ApiException.conflict("Email đã tồn tại");
        user.updateProfile(email, fullName, phone, user.getDepartment());
        return user;
    }

    private void apply(UserAccount user, UserRequest request) {
        Department department = request.departmentId() == null ? null
                : departments.findById(request.departmentId())
                        .orElseThrow(() -> ApiException.notFound("Không tìm thấy phòng ban"));
        Set<Role> selected = new HashSet<>(roles.findByNameIn(
                request.roles() == null || request.roles().isEmpty() ? Set.of(RoleName.REQUESTER) : request.roles()));
        if (selected.isEmpty())
            throw new IllegalArgumentException("Vai trò không hợp lệ");
        user.updateProfile(request.email(), request.fullName(), request.phone(), department);
        user.setRoles(selected);
    }
}
