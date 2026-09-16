package com.example.quanlybaotri.ticket.persistence;

import com.example.quanlybaotri.ticket.domain.WorkLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkLogRepository extends JpaRepository<WorkLog, UUID> {
    List<WorkLog> findByTicketIdOrderByCreatedAtDesc(UUID ticketId);
}
