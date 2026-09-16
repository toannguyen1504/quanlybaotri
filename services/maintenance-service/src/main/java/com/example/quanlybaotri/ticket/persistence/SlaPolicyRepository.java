package com.example.quanlybaotri.ticket.persistence;

import com.example.quanlybaotri.ticket.domain.SlaPolicy;
import com.example.quanlybaotri.ticket.domain.TicketPriority;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, UUID> {
    Optional<SlaPolicy> findByPriorityAndActiveTrue(TicketPriority priority);
}
