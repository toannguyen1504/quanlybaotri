package com.example.quanlybaotri.cost.persistence;
import com.example.quanlybaotri.cost.domain.TicketCostSummary;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TicketCostSummaryRepository extends JpaRepository<TicketCostSummary,UUID> {
    java.util.List<TicketCostSummary> findByStatus(com.example.quanlybaotri.cost.domain.CostStatus status);
}
