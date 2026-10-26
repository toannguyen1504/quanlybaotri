package com.example.quanlybaotri.inventory.persistence;

import com.example.quanlybaotri.inventory.domain.StockMovement;
import com.example.quanlybaotri.inventory.domain.StockMovementType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface StockMovementRepository
    extends JpaRepository<StockMovement, UUID>, JpaSpecificationExecutor<StockMovement> {
    @EntityGraph(attributePaths = "part")
    Page<StockMovement> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "part")
    Page<StockMovement> findAll(
        org.springframework.data.jpa.domain.Specification<StockMovement> specification,
        Pageable pageable
    );

    @Query("select coalesce(sum(abs(m.quantity)), 0) from StockMovement m where m.type = :type and m.createdAt >= :from and m.createdAt < :to")
    BigDecimal quantityBetween(
        @Param("type") StockMovementType type,
        @Param("from") Instant from,
        @Param("to") Instant to
    );
}
