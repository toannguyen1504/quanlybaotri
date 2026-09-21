package com.example.quanlybaotri.ticket.persistence;

import com.example.quanlybaotri.ticket.domain.MaintenanceTicket;
import com.example.quanlybaotri.ticket.domain.TicketStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TicketRepository
    extends JpaRepository<MaintenanceTicket, UUID>, JpaSpecificationExecutor<MaintenanceTicket>
{
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
}
