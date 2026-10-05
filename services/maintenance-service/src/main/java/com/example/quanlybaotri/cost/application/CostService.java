package com.example.quanlybaotri.cost.application;

import com.example.quanlybaotri.cost.api.CostController.*;
import com.example.quanlybaotri.cost.domain.*;
import com.example.quanlybaotri.cost.persistence.*;
import com.example.quanlybaotri.identity.domain.RoleName;
import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.shared.security.CurrentUser.Actor;
import com.example.quanlybaotri.ticket.application.TicketEventService;
import com.example.quanlybaotri.ticket.domain.*;
import com.example.quanlybaotri.ticket.persistence.TicketRepository;
import com.example.quanlybaotri.ticket.domain.WorkLog;
import java.math.*;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CostService {
    private final TicketRepository tickets;
    private final TicketCostSummaryRepository summaries;
    private final TicketCostItemRepository items;
    private final LaborRateRepository rates;
    private final TicketEventService events;
    private final InventoryCostClient inventory;
    private final boolean enforceApproval;

    public CostService(TicketRepository tickets, TicketCostSummaryRepository summaries, TicketCostItemRepository items, LaborRateRepository rates, TicketEventService events, InventoryCostClient inventory, @Value("${app.costing.enforce-approval:false}") boolean enforceApproval) {
        this.tickets=tickets; this.summaries=summaries; this.items=items; this.rates=rates; this.events=events; this.inventory=inventory; this.enforceApproval=enforceApproval;
    }

    @Transactional
    public void createForTicket(UUID ticketId) { summaries.findById(ticketId).orElseGet(() -> summaries.save(new TicketCostSummary(ticketId))); }

    @Transactional
    public CostView get(UUID ticketId, Actor actor) {
        MaintenanceTicket ticket=find(ticketId); visible(ticket,actor);
        TicketCostSummary summary=summary(ticketId);
        boolean detail=elevated(actor)||actor.id().equals(ticket.getAssigneeId());
        List<CostItemView> rows=detail?items.findByTicketIdOrderByIncurredAtAscCreatedAtAsc(ticketId).stream().map(CostItemView::from).toList():List.of();
        boolean complete=!items.existsByTicketIdAndCalculationStatus(ticketId,CalculationStatus.MISSING_RATE);
        return CostView.from(summary,rows,detail,complete);
    }

    @Transactional
    public CostItemView add(UUID ticketId, ManualCostRequest request, Actor actor) {
        MaintenanceTicket ticket=find(ticketId); assigned(ticket,actor); editable(summary(ticketId)); manualCategory(request.category());
        TicketCostItem item=TicketCostItem.manual(ticketId,request.category(),request.description(),request.quantity(),request.unit(),request.unitCost(),request.incurredAt()==null?Instant.now():request.incurredAt(),request.vendor(),request.referenceNumber(),actor.id());
        item=items.save(item); recalculate(ticketId); return CostItemView.from(item);
    }

    @Transactional
    public CostItemView update(UUID ticketId, UUID itemId, ManualCostRequest request, Actor actor) {
        MaintenanceTicket ticket=find(ticketId); assigned(ticket,actor); editable(summary(ticketId)); manualCategory(request.category());
        TicketCostItem item=item(ticketId,itemId); if(item.getSourceType()!=CostSourceType.MANUAL) throw ApiException.conflict("Không thể sửa khoản chi phí tự động");
        item.apply(request.category(),request.description(),request.quantity(),request.unit(),request.unitCost(),request.incurredAt()==null?item.getIncurredAt():request.incurredAt(),request.vendor(),request.referenceNumber()); recalculate(ticketId); return CostItemView.from(item);
    }

    @Transactional
    public void delete(UUID ticketId, UUID itemId, Actor actor) {
        MaintenanceTicket ticket=find(ticketId); assigned(ticket,actor); editable(summary(ticketId)); TicketCostItem item=item(ticketId,itemId);
        if(item.getSourceType()!=CostSourceType.MANUAL) throw ApiException.conflict("Không thể xóa khoản chi phí tự động"); items.delete(item); items.flush(); recalculate(ticketId);
    }

    @Transactional
    public CostView submit(UUID ticketId, Actor actor) {
        MaintenanceTicket ticket=find(ticketId); if(!actor.id().equals(ticket.getAssigneeId())) throw ApiException.forbidden("Chỉ kỹ thuật viên phụ trách được gửi chi phí");
        if(ticket.getStatus()!=TicketStatus.RESOLVED) throw ApiException.conflict("Chỉ gửi duyệt chi phí khi phiếu đã xử lý xong");
        TicketCostSummary summary=summary(ticketId); editable(summary); reconcileForMigration(ticketId); if(items.existsByTicketIdAndCalculationStatus(ticketId,CalculationStatus.MISSING_RATE)) throw ApiException.conflict("Chưa có đơn giá cho một hoặc nhiều work log");
        recalculate(ticketId); summary.submit(actor.id()); record(ticket,actor,"COST_SUBMITTED","Đã gửi bảng chi phí để duyệt"); return CostView.from(summary,items.findByTicketIdOrderByIncurredAtAscCreatedAtAsc(ticketId).stream().map(CostItemView::from).toList(),true,true);
    }

    @Transactional
    public CostView approve(UUID ticketId, Actor actor) {
        requireElevated(actor); MaintenanceTicket ticket=find(ticketId); TicketCostSummary summary=summary(ticketId);
        if(summary.getStatus()!=CostStatus.READY) throw ApiException.conflict("Bảng chi phí chưa sẵn sàng để duyệt");
        if(items.existsByTicketIdAndCalculationStatus(ticketId,CalculationStatus.MISSING_RATE)) throw ApiException.conflict("Không thể duyệt khi còn chi phí nhân công thiếu đơn giá");
        recalculate(ticketId); summary.approve(actor.id()); record(ticket,actor,"COST_APPROVED","Đã duyệt bảng chi phí"); return get(ticketId,actor);
    }

    @Transactional
    public CostView reject(UUID ticketId,String reason,Actor actor) {
        requireElevated(actor); MaintenanceTicket ticket=find(ticketId); TicketCostSummary summary=summary(ticketId);
        if(summary.getStatus()!=CostStatus.READY) throw ApiException.conflict("Bảng chi phí chưa sẵn sàng để từ chối"); summary.reject(actor.id(),reason.trim()); record(ticket,actor,"COST_REJECTED",reason.trim()); return get(ticketId,actor);
    }

    @Transactional
    public void ensureApproved(UUID ticketId) { if(enforceApproval&&summary(ticketId).getStatus()!=CostStatus.APPROVED) throw ApiException.conflict("Bảng chi phí phải được duyệt trước khi đóng phiếu"); }
    @Transactional public void reopen(UUID ticketId){summary(ticketId).reopen("Phiếu được mở lại");}

    @Transactional
    public void addWorkLog(WorkLog log) {
        if(log.getMinutesSpent()<=0||items.findBySourceTypeAndSourceId(CostSourceType.WORK_LOG,log.getId()).isPresent()) return;
        editable(summary(log.getTicket().getId()));
        BigDecimal rate=rateAt(log.getTechnicianId(),log.getCreatedAt());
        items.save(TicketCostItem.labor(log.getTicket().getId(),log.getId(),"Nhân công: "+log.getContent(),log.getMinutesSpent(),rate,log.getCreatedAt(),log.getTechnicianId())); recalculate(log.getTicket().getId());
    }

    @Transactional
    public void addPart(UUID ticketId,UUID sourceId,String description,BigDecimal quantity,String unit,BigDecimal unitCost,Instant incurredAt,UUID actor) {
        if(items.findBySourceTypeAndSourceId(CostSourceType.INVENTORY,sourceId).isPresent()) return;
        editable(summary(ticketId));
        items.save(TicketCostItem.automatic(ticketId,CostCategory.PART,CostSourceType.INVENTORY,sourceId,description,quantity,unit,unitCost,incurredAt,actor)); recalculate(ticketId);
    }

    @Transactional
    public int repriceMissingLabor(){Set<UUID> affected=new HashSet<>();int updated=0;for(TicketCostItem item:items.findByCalculationStatus(CalculationStatus.MISSING_RATE)){BigDecimal rate=rateAt(item.getCreatedById(),item.getIncurredAt());if(rate!=null){item.setRate(rate);affected.add(item.getTicketId());updated++;}}affected.forEach(this::recalculate);return updated;}

    @Transactional
    public int reconcileForMigration(UUID ticketId) {
        TicketCostSummary summary=summary(ticketId);
        if(summary.getStatus()==CostStatus.APPROVED) return 0;
        List<InventoryCostClient.PartCostRef> authoritative=inventory.parts(ticketId);
        int inserted=0;
        for(InventoryCostClient.PartCostRef part:authoritative) {
            if(items.findBySourceTypeAndSourceId(CostSourceType.INVENTORY,part.usageId()).isPresent()) continue;
            items.save(TicketCostItem.automatic(ticketId,CostCategory.PART,CostSourceType.INVENTORY,part.usageId(),"Linh kiện "+part.partCode()+" - "+part.partName(),part.quantity(),part.unit(),part.unitCost(),part.usedAt(),part.usedById()));
            inserted++;
        }
        if(!authoritative.isEmpty()) {
            BigDecimal inventoryTotal=authoritative.stream().map(part->part.quantity().multiply(part.unitCost())).reduce(BigDecimal.ZERO,BigDecimal::add);
            items.findBySourceTypeAndSourceId(CostSourceType.MIGRATION,ticketId).ifPresent(legacy->{
                BigDecimal old=summary.getLegacyPartsCost()==null?BigDecimal.ZERO:summary.getLegacyPartsCost();
                BigDecimal remainder=old.subtract(inventoryTotal);
                if(remainder.signum()>0) legacy.adjustMigrationRemainder(remainder); else items.delete(legacy);
            });
        }
        items.flush();recalculate(ticketId);
        return inserted;
    }

    private BigDecimal rateAt(UUID technicianId,Instant at){return rates.findByTechnicianIdOrderByEffectiveFromDesc(technicianId).stream().filter(r->r.applies(at)).findFirst().or(()->rates.findByTechnicianIdIsNullOrderByEffectiveFromDesc().stream().filter(r->r.applies(at)).findFirst()).map(LaborRate::getHourlyRate).orElse(null);}
    private TicketCostSummary recalculate(UUID ticketId){Map<CostCategory,BigDecimal> totals=new EnumMap<>(CostCategory.class); for(TicketCostItem item:items.findByTicketIdOrderByIncurredAtAscCreatedAtAsc(ticketId)) if(item.getAmount()!=null) totals.merge(item.getCategory(),item.getAmount(),BigDecimal::add); TicketCostSummary s=summary(ticketId); s.totals(total(totals,CostCategory.PART).add(total(totals,CostCategory.LEGACY)),total(totals,CostCategory.LABOR),total(totals,CostCategory.OUTSOURCED),total(totals,CostCategory.TRAVEL),total(totals,CostCategory.OTHER)); return s;}
    private BigDecimal total(Map<CostCategory,BigDecimal> totals,CostCategory category){return totals.getOrDefault(category,BigDecimal.ZERO);}
    private TicketCostSummary summary(UUID id){return summaries.findById(id).orElseGet(()->summaries.save(new TicketCostSummary(id)));}
    private MaintenanceTicket find(UUID id){return tickets.findById(id).orElseThrow(()->ApiException.notFound("Không tìm thấy phiếu"));}
    private TicketCostItem item(UUID ticketId,UUID id){TicketCostItem item=items.findById(id).orElseThrow(()->ApiException.notFound("Không tìm thấy khoản chi phí"));if(!item.getTicketId().equals(ticketId))throw ApiException.conflict("Khoản chi phí không thuộc phiếu");return item;}
    private void editable(TicketCostSummary summary){if(summary.getStatus()!=CostStatus.DRAFT)throw ApiException.conflict("Bảng chi phí đang chờ duyệt hoặc đã được duyệt");}
    private void manualCategory(CostCategory category){if(!Set.of(CostCategory.OUTSOURCED,CostCategory.TRAVEL,CostCategory.OTHER).contains(category))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_COST_CATEGORY","Chỉ được nhập tay chi phí thuê ngoài, di chuyển hoặc khoản khác");}
    private void visible(MaintenanceTicket t,Actor a){if(a.customer())throw ApiException.forbidden("Chi phí bảo trì là dữ liệu nội bộ");if(!elevated(a)&&!a.id().equals(t.getAssigneeId()))throw ApiException.forbidden("Không có quyền xem chi phí");}
    private void assigned(MaintenanceTicket t,Actor a){if(!a.has(RoleName.TECHNICIAN)||!a.id().equals(t.getAssigneeId()))throw ApiException.forbidden("Chỉ kỹ thuật viên phụ trách được cập nhật chi phí");}
    private boolean elevated(Actor a){return a.has(RoleName.ADMIN)||a.has(RoleName.MANAGER);}
    private void requireElevated(Actor a){if(!elevated(a))throw ApiException.forbidden("Chỉ quản lý được duyệt chi phí");}
    private void record(MaintenanceTicket t,Actor a,String action,String description){events.record(t,a.id(),action,t.getStatus(),t.getStatus(),description,Map.of("costStatus",summary(t.getId()).getStatus().name()));}
}
