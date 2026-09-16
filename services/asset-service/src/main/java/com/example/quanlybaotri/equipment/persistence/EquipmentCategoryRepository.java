package com.example.quanlybaotri.equipment.persistence;

import com.example.quanlybaotri.equipment.domain.EquipmentCategory;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipmentCategoryRepository extends JpaRepository<EquipmentCategory, UUID> {
    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);
}
