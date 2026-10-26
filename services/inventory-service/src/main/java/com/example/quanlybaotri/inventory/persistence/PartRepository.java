package com.example.quanlybaotri.inventory.persistence;

import com.example.quanlybaotri.inventory.domain.Part;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.Param;

public interface PartRepository extends JpaRepository<Part, UUID>, JpaSpecificationExecutor<Part> {
    boolean existsByCodeIgnoreCase(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Part p where p.id=:id")
    Optional<Part> findLocked(@Param("id") UUID id);

    @Query("""
        select p from Part p
        where p.active = true
          and (:q = '' or lower(p.code) like lower(concat('%', :q, '%'))
               or lower(p.name) like lower(concat('%', :q, '%')))
        order by p.code asc
        """)
    Page<Part> searchActive(@Param("q") String query, Pageable pageable);

    @Query("select coalesce(sum(p.currentStock * p.defaultUnitCost), 0) from Part p")
    java.math.BigDecimal inventoryValue();

    long countByActiveTrue();

    @Query("select count(p) from Part p where p.currentStock = 0")
    long countOutOfStock();

    @Query("select count(p) from Part p where p.currentStock > 0 and p.currentStock <= p.minimumStock")
    long countLowStock();
}
