package com.example.quanlybaotri.cost.api;

import com.example.quanlybaotri.cost.application.CostService;
import com.example.quanlybaotri.cost.domain.*;
import com.example.quanlybaotri.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tickets/{ticketId}")
public class CostController {
    private final CostService costs; private final CurrentUser current;
    public CostController(CostService costs,CurrentUser current){this.costs=costs;this.current=current;}
    @GetMapping("/costs") public CostView get(@PathVariable UUID ticketId){return costs.get(ticketId,current.require());}
    @PostMapping("/cost-items") @ResponseStatus(HttpStatus.CREATED) public CostItemView add(@PathVariable UUID ticketId,@Valid @RequestBody ManualCostRequest request){return costs.add(ticketId,request,current.require());}
    @PutMapping("/cost-items/{itemId}") public CostItemView update(@PathVariable UUID ticketId,@PathVariable UUID itemId,@Valid @RequestBody ManualCostRequest request){return costs.update(ticketId,itemId,request,current.require());}
    @DeleteMapping("/cost-items/{itemId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID ticketId,@PathVariable UUID itemId){costs.delete(ticketId,itemId,current.require());}
    @PostMapping("/costs/submit") public CostView submit(@PathVariable UUID ticketId){return costs.submit(ticketId,current.require());}
    @PostMapping("/costs/approve") public CostView approve(@PathVariable UUID ticketId){return costs.approve(ticketId,current.require());}
    @PostMapping("/costs/reject") public CostView reject(@PathVariable UUID ticketId,@Valid @RequestBody RejectRequest request){return costs.reject(ticketId,request.reason(),current.require());}

    public record ManualCostRequest(@NotNull CostCategory category,@NotBlank @Size(max=500) String description,@NotNull @DecimalMin("0.001") BigDecimal quantity,@NotBlank @Size(max=30) String unit,@NotNull @DecimalMin("0") BigDecimal unitCost,Instant incurredAt,@Size(max=200) String vendor,@Size(max=100) String referenceNumber){}
    public record RejectRequest(@NotBlank @Size(max=1000) String reason){}
    public record CostItemView(UUID id,CostCategory category,CostSourceType sourceType,CalculationStatus calculationStatus,String description,BigDecimal quantity,String unit,BigDecimal unitCost,BigDecimal amount,Instant incurredAt,String vendor,String referenceNumber,UUID createdById){public static CostItemView from(TicketCostItem i){return new CostItemView(i.getId(),i.getCategory(),i.getSourceType(),i.getCalculationStatus(),i.getDescription(),i.getQuantity(),i.getUnit(),i.getUnitCost(),i.getAmount(),i.getIncurredAt(),i.getVendor(),i.getReferenceNumber(),i.getCreatedById());}}
    public record CostView(CostStatus status,BigDecimal partsTotal,BigDecimal laborTotal,BigDecimal outsourcedTotal,BigDecimal travelTotal,BigDecimal otherTotal,BigDecimal grandTotal,boolean complete,boolean detailVisible,List<CostItemView> items,UUID submittedById,Instant submittedAt,UUID approvedById,Instant approvedAt,String rejectionReason){public static CostView from(TicketCostSummary s,List<CostItemView> items,boolean detail,boolean complete){return new CostView(s.getStatus(),s.getPartsTotal(),s.getLaborTotal(),s.getOutsourcedTotal(),s.getTravelTotal(),s.getOtherTotal(),s.getGrandTotal(),complete,detail,items,s.getSubmittedById(),s.getSubmittedAt(),s.getApprovedById(),s.getApprovedAt(),s.getRejectionReason());}}
}
