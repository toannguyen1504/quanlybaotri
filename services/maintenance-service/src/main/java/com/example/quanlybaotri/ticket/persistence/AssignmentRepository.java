package com.example.quanlybaotri.ticket.persistence;

import com.example.quanlybaotri.ticket.domain.TicketAssignment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignmentRepository extends JpaRepository<TicketAssignment, UUID> {
    Optional<TicketAssignment> findFirstByTicketIdAndUnassignedAtIsNullOrderByAssignedAtDesc(UUID ticketId);
}
