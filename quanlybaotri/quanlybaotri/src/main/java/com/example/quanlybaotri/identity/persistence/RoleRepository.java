package com.example.quanlybaotri.identity.persistence;

import com.example.quanlybaotri.identity.domain.Role;
import com.example.quanlybaotri.identity.domain.RoleName;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByName(RoleName name);

    List<Role> findByNameIn(Collection<RoleName> names);
}
