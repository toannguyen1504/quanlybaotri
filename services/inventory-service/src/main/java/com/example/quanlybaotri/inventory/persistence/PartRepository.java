package com.example.quanlybaotri.inventory.persistence;

import com.example.quanlybaotri.inventory.domain.Part;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PartRepository extends JpaRepository<Part, UUID> {
    boolean existsByCodeIgnoreCase(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Part p where p.id=:id")
    Optional<Part> findLocked(@Param("id") UUID id);
}
