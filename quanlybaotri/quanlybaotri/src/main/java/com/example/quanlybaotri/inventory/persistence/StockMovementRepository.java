package com.example.quanlybaotri.inventory.persistence;

import com.example.quanlybaotri.inventory.domain.StockMovement;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {
    @EntityGraph(attributePaths = { "part", "ticket", "actor" })
    Page<StockMovement> findAll(Pageable pageable);
}
