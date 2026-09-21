package com.example.quanlybaotri.equipment.persistence;

import com.example.quanlybaotri.equipment.domain.Equipment;
import com.example.quanlybaotri.equipment.domain.EquipmentStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EquipmentRepository extends JpaRepository<Equipment, UUID> {
    boolean existsByAssetCodeIgnoreCase(String code);

    long countByCategoryId(UUID categoryId);

    @Override
    @EntityGraph(attributePaths = { "category" })
    Page<Equipment> findAll(Pageable pageable);

    @EntityGraph(attributePaths = { "category" })
    Page<Equipment> findByStatus(EquipmentStatus status, Pageable pageable);

    @EntityGraph(attributePaths = { "category" })
    @Query(
        """
        select e from Equipment e
        where (
            lower(e.assetCode) like lower(concat('%', :q, '%'))
            or lower(e.name) like lower(concat('%', :q, '%'))
        )
        and (:status is null or e.status = :status)
        """
    )
    Page<Equipment> search(
        @Param("q") String q,
        @Param("status") EquipmentStatus status,
        Pageable pageable
    );

    @Override
    @EntityGraph(attributePaths = { "category" })
    java.util.Optional<Equipment> findById(UUID id);

    @EntityGraph(attributePaths = { "category" })
    java.util.List<Equipment> findByIdIn(java.util.Collection<UUID> ids);
}
