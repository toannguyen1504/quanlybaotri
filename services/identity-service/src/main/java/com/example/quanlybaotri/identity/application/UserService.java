package com.example.quanlybaotri.identity.application;

import com.example.quanlybaotri.identity.api.UserController.UserRequest;
import com.example.quanlybaotri.identity.api.UserController.UserView;
import com.example.quanlybaotri.identity.domain.Role;
import com.example.quanlybaotri.identity.domain.RoleName;
import com.example.quanlybaotri.identity.domain.UserAccount;
import com.example.quanlybaotri.identity.persistence.RoleRepository;
import com.example.quanlybaotri.identity.persistence.UserRepository;
import com.example.quanlybaotri.organization.client.OrganizationClient;
import com.example.quanlybaotri.shared.api.ApiException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class UserService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final OrganizationClient organizations;
    private final PasswordEncoder encoder;
    private final TransactionTemplate transactions;

    public UserService(UserRepository users, RoleRepository roles, OrganizationClient organizations,
            PasswordEncoder encoder, TransactionTemplate transactions) {
        this.users = users;
        this.roles = roles;
        this.organizations = organizations;
        this.encoder = encoder;
        this.transactions = transactions;
    }

    public Page<UserView> list(Pageable pageable) {
        Page<UserAccount> page = users.findAll(pageable);
        var departments = organizations.resolve(page.getContent().stream().map(UserAccount::getDepartmentId)
                .filter(java.util.Objects::nonNull).toList());
        return page.map(user -> UserView.from(user, user.getDepartmentId() == null ? null
                : departments.get(user.getDepartmentId())));
    }

    public UserView create(UserRequest request) {
        if (request.password() == null || request.password().length() < 8) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Mật khẩu phải có ít nhất 8 ký tự");
        }
        OrganizationClient.DepartmentRef department = validateDepartment(request.departmentId());
        UserAccount user = transactions.execute(status -> {
            if (users.existsByUsernameIgnoreCase(request.username()))
                throw ApiException.conflict("Tên đăng nhập đã tồn tại");
            if (users.existsByEmailIgnoreCase(request.email()))
                throw ApiException.conflict("Email đã tồn tại");
            UserAccount created = new UserAccount(request.username(), request.email(),
                    encoder.encode(request.password()), request.fullName());
            apply(created, request, department);
            return users.save(created);
        });
        return UserView.from(user, department);
    }

    public UserView update(UUID id, UserRequest request) {
        OrganizationClient.DepartmentRef department = validateDepartment(request.departmentId());
        UserAccount user = transactions.execute(status -> {
            UserAccount found = users.findWithRolesById(id)
                    .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));
            apply(found, request, department);
            found.setEnabled(request.enabled());
            return found;
        });
        return UserView.from(user, department);
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
        user.updateProfile(email, fullName, phone, user.getDepartmentId());
        return user;
    }

    private OrganizationClient.DepartmentRef validateDepartment(UUID departmentId) {
        if (departmentId == null) return null;
        OrganizationClient.DepartmentRef department = organizations.find(departmentId);
        if (!department.active()) throw ApiException.conflict("Phòng ban đã ngừng hoạt động");
        return department;
    }

    private void apply(UserAccount user, UserRequest request, OrganizationClient.DepartmentRef department) {
        Set<Role> selected = new HashSet<>(roles.findByNameIn(
                request.roles() == null || request.roles().isEmpty() ? Set.of(RoleName.REQUESTER) : request.roles()));
        if (selected.isEmpty()) throw new IllegalArgumentException("Vai trò không hợp lệ");
        user.updateProfile(request.email(), request.fullName(), request.phone(), department == null ? null : department.id());
        user.setRoles(selected);
    }
}
