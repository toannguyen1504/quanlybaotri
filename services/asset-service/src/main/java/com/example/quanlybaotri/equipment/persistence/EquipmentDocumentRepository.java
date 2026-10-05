package com.example.quanlybaotri.equipment.persistence;
import com.example.quanlybaotri.equipment.domain.EquipmentDocument;
import java.util.*;
import org.springframework.data.jpa.repository.*;
public interface EquipmentDocumentRepository extends JpaRepository<EquipmentDocument,UUID>{
    List<EquipmentDocument> findByEquipmentIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID equipmentId);
    List<EquipmentDocument> findTop100ByDeletedAtIsNotNullOrderByDeletedAtAsc();
    @EntityGraph(attributePaths="equipment") Optional<EquipmentDocument> findByIdAndDeletedAtIsNull(UUID id);
}
