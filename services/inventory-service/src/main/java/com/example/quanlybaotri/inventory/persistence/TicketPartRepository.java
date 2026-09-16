package com.example.quanlybaotri.inventory.persistence;

import com.example.quanlybaotri.inventory.domain.TicketPart;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface TicketPartRepository extends JpaRepository<TicketPart, UUID> {
    @EntityGraph(attributePaths = "part")
    List<TicketPart> findByTicketIdOrderByUsedAtDesc(UUID ticketId);
}
