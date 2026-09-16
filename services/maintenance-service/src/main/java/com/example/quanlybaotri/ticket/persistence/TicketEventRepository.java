package com.example.quanlybaotri.ticket.persistence;

import com.example.quanlybaotri.ticket.domain.TicketEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketEventRepository extends JpaRepository<TicketEvent, UUID> {
    List<TicketEvent> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);

    boolean existsByTicketIdAndEventType(UUID ticketId, String eventType);
}
