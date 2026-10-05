package com.example.quanlybaotri.organization.persistence;

import com.example.quanlybaotri.organization.domain.Department;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);

    @Override
    @EntityGraph(attributePaths = {"branch", "region"})
    List<Department> findAll();

    @Override
    @EntityGraph(attributePaths = {"branch", "region"})
    Optional<Department> findById(UUID id);

    @EntityGraph(attributePaths = {"branch", "region"})
    List<Department> findByIdIn(Collection<UUID> ids);

    @EntityGraph(attributePaths = {"branch", "region"})
    List<Department> findByBranchId(UUID branchId);
}
