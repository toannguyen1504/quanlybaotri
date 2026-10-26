package com.example.quanlybaotri.cost.application;

import com.example.quanlybaotri.cost.api.CostController.CostItemView;
import com.example.quanlybaotri.cost.api.CostController.CostView;
import com.example.quanlybaotri.cost.api.CostController.ManualCostRequest;
import com.example.quanlybaotri.cost.domain.*;
import com.example.quanlybaotri.cost.persistence.*;
import com.example.quanlybaotri.identity.domain.RoleName;
import com.example.quanlybaotri.payment.client.PaymentClient;
import com.example.quanlybaotri.payment.client.PaymentClient.PaymentSnapshot;
import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.shared.integration.CommandSource;
import com.example.quanlybaotri.shared.security.CurrentUser.Actor;
import com.example.quanlybaotri.ticket.application.TicketEventService;
import com.example.quanlybaotri.ticket.domain.*;
import com.example.quanlybaotri.ticket.persistence.TicketRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CostService {
    private final TicketRepository tickets;
    private final TicketCostSummaryRepository summaries;
    private final TicketCostItemRepository items;
    private final TicketEventService events;
    private final InventoryCostClient inventory;
    private final PaymentClient payments;

    public CostService(TicketRepository tickets, TicketCostSummaryRepository summaries,
        TicketCostItemRepository items, TicketEventService events,
        InventoryCostClient inventory, PaymentClient payments) {
        this.tickets = tickets;
        this.summaries = summaries;
        this.items = items;
        this.events = events;
        this.inventory = inventory;
        this.payments = payments;
    }

    @Transactional
    public void createForTicket(UUID ticketId) {
        summaries.findById(ticketId).orElseGet(() -> summaries.save(new TicketCostSummary(ticketId)));
    }

    @Transactional
    public CostView get(UUID ticketId, Actor actor) {
        TicketCostSummary summary = summary(ticketId);
        MaintenanceTicket ticket = find(ticketId);
        visible(ticket, actor, summary);
        boolean internal = !actor.customer();
        List<CostItemView> rows = items.findByTicketIdOrderByIncurredAtAscCreatedAtAsc(ticketId)
            .stream().map(internal ? CostItemView::from : CostItemView::forCustomer).toList();
        return CostView.from(summary, rows, internal, true);
    }

    @Transactional
    public CostItemView add(UUID ticketId, ManualCostRequest request, Actor actor) {
        MaintenanceTicket ticket = find(ticketId);
        assigned(ticket, actor);
        editable(summary(ticketId));
        manualCategory(request.category());
        TicketCostItem item = TicketCostItem.manual(ticketId, request.category(), request.description(),
            request.quantity(), request.unit(), request.unitCost(),
            request.incurredAt() == null ? Instant.now() : request.incurredAt(),
            request.vendor(), request.referenceNumber(), actor.id());
        item = items.save(item);
        recalculate(ticketId);
        return CostItemView.from(item);
    }

    @Transactional
    public CostItemView update(UUID ticketId, UUID itemId, ManualCostRequest request, Actor actor) {
        MaintenanceTicket ticket = find(ticketId);
        assigned(ticket, actor);
        editable(summary(ticketId));
        manualCategory(request.category());
        TicketCostItem item = item(ticketId, itemId);
        if (item.getSourceType() != CostSourceType.MANUAL)
            throw ApiException.conflict("Không thể sửa khoản chi phí tự động");
        item.apply(request.category(), request.description(), request.quantity(), request.unit(),
            request.unitCost(), request.incurredAt() == null ? item.getIncurredAt() : request.incurredAt(),
            request.vendor(), request.referenceNumber());
        recalculate(ticketId);
        return CostItemView.from(item);
    }

    @Transactional
    public void delete(UUID ticketId, UUID itemId, Actor actor) {
        MaintenanceTicket ticket = find(ticketId);
        assigned(ticket, actor);
        editable(summary(ticketId));
        TicketCostItem item = item(ticketId, itemId);
        if (item.getSourceType() != CostSourceType.MANUAL)
            throw ApiException.conflict("Không thể xóa khoản chi phí tự động");
        items.delete(item);
        items.flush();
        recalculate(ticketId);
    }

    @Transactional
    public CostView quote(UUID ticketId, Actor actor) {
        MaintenanceTicket ticket = find(ticketId);
        assigned(ticket, actor);
        resolved(ticket);
        TicketCostSummary summary = summary(ticketId);
        editable(summary);
        reconcileForMigration(ticketId);
        recalculate(ticketId);
        summary.quote(actor.id());
        record(ticket, actor, "COST_QUOTED", "Đã gửi báo giá cho khách hàng xác nhận");
        return CostView.from(summary, items.findByTicketIdOrderByIncurredAtAscCreatedAtAsc(ticketId)
            .stream().map(CostItemView::from).toList(), true, true);
    }

    @Transactional
    public CostView accept(UUID ticketId, Actor actor) {
        MaintenanceTicket ticket = find(ticketId);
        requester(ticket, actor);
        resolved(ticket);
        TicketCostSummary summary = summary(ticketId);
        if (summary.getStatus() != CostStatus.QUOTED)
            throw ApiException.conflict("Báo giá không ở trạng thái chờ khách hàng xác nhận");
        summary.accept(actor.id());
        record(ticket, actor, "COST_ACCEPTED", "Khách hàng đã chấp nhận báo giá");
        return get(ticketId, actor);
    }

    @Transactional
    public CostView decline(UUID ticketId, String reason, Actor actor) {
        if (reason == null || reason.isBlank())
            throw new ApiException(HttpStatus.BAD_REQUEST, "DECLINE_REASON_REQUIRED",
                "Phải nhập lý do từ chối báo giá");
        MaintenanceTicket ticket = find(ticketId);
        requester(ticket, actor);
        resolved(ticket);
        TicketCostSummary summary = summary(ticketId);
        if (summary.getStatus() != CostStatus.QUOTED)
            throw ApiException.conflict("Báo giá không ở trạng thái chờ khách hàng xác nhận");
        summary.decline(actor.id(), reason.trim());
        record(ticket, actor, "COST_DECLINED", reason.trim());
        return CostView.from(summary, List.of(), false, true);
    }

    @Transactional
    public void reopen(UUID ticketId) { summary(ticketId).reopen("Phiếu được mở lại"); }

    @Transactional(readOnly = true)
    public void ensureSettled(UUID ticketId) {
        TicketCostSummary cost = summaries.findById(ticketId)
            .orElseThrow(() -> ApiException.conflict("Phiếu chưa có báo giá"));
        if (cost.getStatus() != CostStatus.ACCEPTED)
            throw ApiException.conflict("Báo giá phải được khách hàng chấp nhận trước khi đóng phiếu");
        payments.ensureSettled(ticketId, cost.getGrandTotal());
    }

    @Transactional(readOnly = true)
    public void ensureReopenAllowed(UUID ticketId) { payments.ensureReopenAllowed(ticketId); }

    @Transactional(readOnly = true)
    public Map<UUID, PaymentSnapshot> paymentSnapshots(Collection<UUID> ticketIds) {
        Map<UUID, TicketCostSummary> found = new HashMap<>();
        summaries.findAllById(ticketIds).forEach(cost -> found.put(cost.getTicketId(), cost));
        return payments.snapshots(ticketIds.stream().map(id -> {
            TicketCostSummary cost = found.get(id);
            return new PaymentClient.CostRef(id, cost == null ? null : cost.getStatus().name(),
                cost == null ? null : cost.getGrandTotal());
        }).toList());
    }

    @Transactional
    public void addPart(UUID ticketId, UUID sourceId, String description, BigDecimal quantity,
        String unit, BigDecimal unitCost, Instant incurredAt, UUID actor) {
        if (items.findBySourceTypeAndSourceId(CostSourceType.INVENTORY, sourceId).isPresent()) return;
        editable(summary(ticketId));
        items.save(TicketCostItem.automatic(ticketId, CostCategory.PART, CostSourceType.INVENTORY,
            sourceId, description, quantity, unit, unitCost, incurredAt, actor));
        recalculate(ticketId);
    }

    @Transactional
    public int reconcileForMigration(UUID ticketId) {
        TicketCostSummary summary = summary(ticketId);
        if (summary.getStatus() != CostStatus.DRAFT) return 0;
        List<InventoryCostClient.PartCostRef> authoritative = inventory.parts(ticketId);
        int inserted = 0;
        for (InventoryCostClient.PartCostRef part : authoritative) {
            if (items.findBySourceTypeAndSourceId(CostSourceType.INVENTORY, part.usageId()).isPresent()) continue;
            items.save(TicketCostItem.automatic(ticketId, CostCategory.PART, CostSourceType.INVENTORY,
                part.usageId(), "Linh kiện " + part.partCode() + " - " + part.partName(),
                part.quantity(), part.unit(), part.unitCost(), part.usedAt(), part.usedById()));
            inserted++;
        }
        if (!authoritative.isEmpty()) {
            BigDecimal inventoryTotal = authoritative.stream()
                .map(part -> part.quantity().multiply(part.unitCost()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            items.findBySourceTypeAndSourceId(CostSourceType.MIGRATION, ticketId).ifPresent(legacy -> {
                BigDecimal old = summary.getLegacyPartsCost() == null
                    ? BigDecimal.ZERO : summary.getLegacyPartsCost();
                BigDecimal remainder = old.subtract(inventoryTotal);
                if (remainder.signum() > 0) legacy.adjustMigrationRemainder(remainder);
                else items.delete(legacy);
            });
        }
        items.flush();
        recalculate(ticketId);
        return inserted;
    }

    private TicketCostSummary recalculate(UUID ticketId) {
        Map<CostCategory, BigDecimal> totals = new EnumMap<>(CostCategory.class);
        for (TicketCostItem item : items.findByTicketIdOrderByIncurredAtAscCreatedAtAsc(ticketId))
            if (item.getAmount() != null) totals.merge(item.getCategory(), item.getAmount(), BigDecimal::add);
        TicketCostSummary summary = summary(ticketId);
        summary.totals(total(totals, CostCategory.PART), total(totals, CostCategory.OTHER));
        return summary;
    }

    private BigDecimal total(Map<CostCategory, BigDecimal> totals, CostCategory category) {
        return totals.getOrDefault(category, BigDecimal.ZERO);
    }
    private TicketCostSummary summary(UUID id) {
        return summaries.findById(id).orElseGet(() -> summaries.save(new TicketCostSummary(id)));
    }
    private MaintenanceTicket find(UUID id) {
        return tickets.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu"));
    }
    private TicketCostItem item(UUID ticketId, UUID id) {
        TicketCostItem item = items.findById(id)
            .orElseThrow(() -> ApiException.notFound("Không tìm thấy khoản chi phí"));
        if (!item.getTicketId().equals(ticketId))
            throw ApiException.conflict("Khoản chi phí không thuộc phiếu");
        return item;
    }
    private void editable(TicketCostSummary summary) {
        if (summary.getStatus() != CostStatus.DRAFT)
            throw ApiException.conflict("Báo giá đang chờ khách hàng xác nhận hoặc đã được chấp nhận");
    }
    private void manualCategory(CostCategory category) {
        if (category != CostCategory.OTHER)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COST_CATEGORY",
                "Chỉ được nhập chi phí phát sinh; chi phí linh kiện được tự động ghi nhận từ kho");
    }
    private void visible(MaintenanceTicket ticket, Actor actor, TicketCostSummary summary) {
        if (actor.customer()) {
            requester(ticket, actor);
            if (summary.getStatus() != CostStatus.QUOTED && summary.getStatus() != CostStatus.ACCEPTED)
                throw ApiException.forbidden("Báo giá chưa được gửi cho khách hàng");
            return;
        }
        if (!elevated(actor) && !actor.id().equals(ticket.getAssigneeId()))
            throw ApiException.forbidden("Không có quyền xem báo giá");
    }
    private void assigned(MaintenanceTicket ticket, Actor actor) {
        if (!actor.has(RoleName.TECHNICIAN) || !actor.id().equals(ticket.getAssigneeId()))
            throw ApiException.forbidden("Chỉ kỹ thuật viên phụ trách được cập nhật báo giá");
    }
    private void requester(MaintenanceTicket ticket, Actor actor) {
        if (!actor.customer() || !actor.id().equals(ticket.getRequesterId()) ||
            !actor.canAccess(ticket.getCustomerId(), ticket.getSiteId()))
            throw ApiException.forbidden("Chỉ khách hàng tạo phiếu được xác nhận báo giá");
    }
    private void resolved(MaintenanceTicket ticket) {
        if (ticket.getStatus() != TicketStatus.RESOLVED)
            throw ApiException.conflict("Chỉ gửi hoặc xác nhận báo giá khi phiếu đã xử lý xong");
    }
    private boolean elevated(Actor actor) {
        return actor.has(RoleName.ADMIN) || actor.has(RoleName.MANAGER);
    }
    private void record(MaintenanceTicket ticket, Actor actor, String action, String description) {
        events.record(ticket, actor.id(), action, ticket.getStatus(), ticket.getStatus(), description,
            CommandSource.metadata(Map.of("costStatus", summary(ticket.getId()).getStatus().name())));
    }
}
