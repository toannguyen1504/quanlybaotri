package com.example.quanlybaotri.feedback.persistence;

import com.example.quanlybaotri.feedback.domain.SystemFeedback;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SystemFeedbackRepository
    extends JpaRepository<SystemFeedback, UUID>, JpaSpecificationExecutor<SystemFeedback>
{
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from SystemFeedback f where f.id = :id")
    Optional<SystemFeedback> findLockedById(@Param("id") UUID id);
}
