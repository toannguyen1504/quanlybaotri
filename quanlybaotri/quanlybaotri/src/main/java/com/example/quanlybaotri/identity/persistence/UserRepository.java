package com.example.quanlybaotri.identity.persistence;

import com.example.quanlybaotri.identity.domain.UserAccount;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserRepository extends JpaRepository<UserAccount, UUID>, JpaSpecificationExecutor<UserAccount> {
    @EntityGraph(attributePaths = { "roles", "department" })
    Optional<UserAccount> findWithRolesByUsername(String username);

    @EntityGraph(attributePaths = { "roles", "department" })
    Optional<UserAccount> findWithRolesById(UUID id);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

    @org.springframework.data.jpa.repository.Query("select distinct u from UserAccount u join u.roles r where r.name in :roles and u.enabled=true")
    List<UserAccount> findEnabledByRoleNames(
            @org.springframework.data.repository.query.Param("roles") Collection<com.example.quanlybaotri.identity.domain.RoleName> roles);
}
