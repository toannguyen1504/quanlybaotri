package com.example.quanlybaotri.identity.persistence;

import com.example.quanlybaotri.identity.domain.UserAccount;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserRepository
    extends JpaRepository<UserAccount, UUID>, JpaSpecificationExecutor<UserAccount>
{
    @Override
    @EntityGraph(attributePaths = { "roles", "siteIds", "regionIds" })
    Page<UserAccount> findAll(Specification<UserAccount> specification, Pageable pageable);
    @EntityGraph(attributePaths = { "roles", "siteIds", "regionIds" })
    Optional<UserAccount> findWithRolesByUsername(String username);

    @EntityGraph(attributePaths = { "roles", "siteIds", "regionIds" })
    @org.springframework.data.jpa.repository.Query(
        "select distinct u from UserAccount u where lower(u.username)=lower(:identifier) or lower(u.email)=lower(:identifier)"
    )
    Optional<UserAccount> findWithRolesByUsernameOrEmail(
        @org.springframework.data.repository.query.Param("identifier") String identifier
    );

    @EntityGraph(attributePaths = { "roles", "siteIds", "regionIds" })
    Optional<UserAccount> findWithRolesByGoogleSubject(String googleSubject);

    @EntityGraph(attributePaths = { "roles", "siteIds", "regionIds" })
    Optional<UserAccount> findWithRolesById(UUID id);

    @EntityGraph(attributePaths = { "roles", "siteIds", "regionIds" })
    List<UserAccount> findByIdIn(Collection<UUID> ids);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

    @EntityGraph(attributePaths = { "roles", "siteIds", "regionIds" })
    @org.springframework.data.jpa.repository.Query(
        "select distinct u from UserAccount u join u.roles r where r.name in :roles and u.enabled=true"
    )
    List<UserAccount> findEnabledByRoleNames(
        @org.springframework.data.repository.query.Param(
            "roles"
        ) Collection<com.example.quanlybaotri.identity.domain.RoleName> roles
    );

    @EntityGraph(attributePaths = { "roles", "siteIds", "regionIds" })
    @org.springframework.data.jpa.repository.Query(
        "select distinct u from UserAccount u join u.roles r where r.name in :roles"
    )
    List<UserAccount> findByRoleNames(
        @org.springframework.data.repository.query.Param(
            "roles"
        ) Collection<com.example.quanlybaotri.identity.domain.RoleName> roles
    );
}
