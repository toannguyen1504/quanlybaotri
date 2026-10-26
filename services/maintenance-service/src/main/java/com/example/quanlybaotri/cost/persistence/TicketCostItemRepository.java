package com.example.quanlybaotri.cost.persistence;
import com.example.quanlybaotri.cost.domain.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TicketCostItemRepository extends JpaRepository<TicketCostItem,UUID> {
    List<TicketCostItem> findByTicketIdOrderByIncurredAtAscCreatedAtAsc(UUID ticketId);
    Optional<TicketCostItem> findBySourceTypeAndSourceId(CostSourceType sourceType,UUID sourceId);
}
