package com.example.quanlybaotri.inventory.api;

import com.example.quanlybaotri.inventory.domain.TicketPart;
import com.example.quanlybaotri.inventory.persistence.TicketPartRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/tickets")
public class InternalInventoryController {
    private final TicketPartRepository usages;
    public InternalInventoryController(TicketPartRepository usages){this.usages=usages;}
    @GetMapping("/{ticketId}/parts")
    public List<PartCostRef> parts(@PathVariable UUID ticketId){return usages.findByTicketIdOrderByUsedAtDesc(ticketId).stream().map(PartCostRef::from).toList();}
    public record PartCostRef(UUID usageId,UUID partId,String partCode,String partName,String unit,BigDecimal quantity,BigDecimal unitCost,UUID usedById,Instant usedAt){static PartCostRef from(TicketPart p){return new PartCostRef(p.getId(),p.getPart().getId(),p.getPart().getCode(),p.getPart().getName(),p.getPart().getUnit(),p.getQuantity(),p.getUnitCost(),p.getUsedById(),p.getUsedAt());}}
}
