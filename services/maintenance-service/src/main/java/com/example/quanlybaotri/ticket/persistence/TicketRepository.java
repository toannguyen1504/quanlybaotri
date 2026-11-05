package com.example.quanlybaotri.ticket.persistence;

import com.example.quanlybaotri.ticket.domain.MaintenanceTicket;
import com.example.quanlybaotri.ticket.domain.TicketStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface TicketRepository
    extends JpaRepository<MaintenanceTicket, UUID>, JpaSpecificationExecutor<MaintenanceTicket>
{
    java.util.Optional<MaintenanceTicket> findByCreationIdempotencyKey(String creationIdempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from MaintenanceTicket t where t.id = :id")
    java.util.Optional<MaintenanceTicket> findLockedById(@Param("id") UUID id);

    long countByEquipmentIdAndIdNotAndStatusNotIn(
        UUID equipmentId,
        UUID id,
        Collection<TicketStatus> statuses
    );

    List<MaintenanceTicket> findByStatusNotInAndResolutionDueAtBefore(
        Collection<TicketStatus> statuses,
        Instant instant
    );

    List<MaintenanceTicket> findByStatusNotIn(Collection<TicketStatus> statuses);

    Page<MaintenanceTicket> findByAssigneeIdAndStatusInOrderBySubmittedAtDesc(
        UUID assigneeId,
        Collection<TicketStatus> statuses,
        Pageable pageable
    );
}
