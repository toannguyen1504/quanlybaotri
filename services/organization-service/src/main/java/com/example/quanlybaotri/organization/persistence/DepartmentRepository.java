package com.example.quanlybaotri.organization.persistence;

import com.example.quanlybaotri.organization.domain.Department;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);
    List<Department> findByIdIn(Collection<UUID> ids);
}
