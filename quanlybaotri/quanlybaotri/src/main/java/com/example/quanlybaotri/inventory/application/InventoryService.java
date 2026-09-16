package com.example.quanlybaotri.inventory.application;

import com.example.quanlybaotri.identity.domain.UserAccount;
import com.example.quanlybaotri.inventory.api.InventoryController.*;
import com.example.quanlybaotri.inventory.domain.*;
import com.example.quanlybaotri.inventory.persistence.*;
import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.ticket.application.TicketEventService;
import com.example.quanlybaotri.ticket.domain.*;
import com.example.quanlybaotri.ticket.persistence.TicketRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
    private final PartRepository parts;
    private final StockMovementRepository movements;
    private final TicketPartRepository usages;
    private final TicketRepository tickets;
    private final TicketEventService events;

    public InventoryService(PartRepository p, StockMovementRepository m, TicketPartRepository u, TicketRepository t,
            TicketEventService e) {
        parts = p;
        movements = m;
        usages = u;
        tickets = t;
        events = e;
    }

    @Transactional(readOnly = true)
    public Page<PartView> list(Pageable p) {
        return parts.findAll(p).map(PartView::from);
    }

    @Transactional
    public PartView create(PartRequest r) {
        if (parts.existsByCodeIgnoreCase(r.code()))
            throw ApiException.conflict("Mã linh kiện đã tồn tại");
        return PartView.from(parts.save(new Part(r.code().trim().toUpperCase(), r.name().trim(), r.unit().trim(),
                r.minimumStock(), r.defaultUnitCost())));
    }

    @Transactional
    public PartView update(UUID id, PartRequest r) {
        Part p = find(id);
        p.update(r.code().trim().toUpperCase(), r.name().trim(), r.unit().trim(), r.minimumStock(), r.defaultUnitCost(),
                r.active());
        return PartView.from(p);
    }

    @Transactional
    public PartView receive(UUID id, MovementRequest r, UserAccount actor) {
        Part p = locked(id);
        positive(r.quantity());
        p.add(r.quantity());
        movements.save(new StockMovement(p, null, actor, StockMovementType.IN, r.quantity(), p.getCurrentStock(),
                r.unitCost(), required(r.reason())));
        return PartView.from(p);
    }

    @Transactional
    public PartView adjust(UUID id, AdjustmentRequest r, UserAccount actor) {
        Part p = locked(id);
        if (r.newBalance().signum() < 0)
            throw ApiException.conflict("Tồn kho không thể âm");
        BigDecimal delta = r.newBalance().subtract(p.getCurrentStock());
        p.adjust(r.newBalance());
        movements.save(new StockMovement(p, null, actor, StockMovementType.ADJUSTMENT, delta, p.getCurrentStock(), null,
                required(r.reason())));
        return PartView.from(p);
    }

    @Transactional
    public TicketPartView use(UUID ticketId, UsePartRequest r, UserAccount actor) {
        MaintenanceTicket t = tickets.findById(ticketId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu"));
        if (t.getAssignee() == null || !t.getAssignee().getId().equals(actor.getId()))
            throw ApiException.forbidden("Phiếu không được phân công cho bạn");
        if (!Set.of(TicketStatus.IN_PROGRESS, TicketStatus.WAITING_PARTS).contains(t.getStatus()))
            throw ApiException.conflict("Không thể dùng linh kiện ở trạng thái hiện tại");
        positive(r.quantity());
        Part p = locked(r.partId());
        if (!p.isActive())
            throw ApiException.conflict("Linh kiện đã ngừng sử dụng");
        p.issue(r.quantity());
        BigDecimal cost = r.unitCost() == null ? p.getDefaultUnitCost() : r.unitCost();
        t.addPartCost(r.quantity().multiply(cost).setScale(2, RoundingMode.HALF_UP));
        TicketPart use = usages.save(new TicketPart(t, p, r.quantity(), cost, actor));
        movements.save(new StockMovement(p, t, actor, StockMovementType.OUT, r.quantity().negate(), p.getCurrentStock(),
                cost, "Sử dụng cho phiếu " + t.getCode()));
        events.record(t, actor, "PART_USED", t.getStatus(), t.getStatus(),
                "Sử dụng " + r.quantity() + " " + p.getUnit() + " " + p.getName(),
                Map.of("partId", p.getId(), "quantity", r.quantity(), "unitCost", cost));
        if (p.getCurrentStock().compareTo(p.getMinimumStock()) <= 0)
            events.record(t, null, "PART_LOW_STOCK", t.getStatus(), t.getStatus(),
                    "Linh kiện " + p.getCode() + " đã chạm mức tồn tối thiểu",
                    Map.of("partId", p.getId(), "balance", p.getCurrentStock()));
        return TicketPartView.from(use);
    }

    @Transactional(readOnly = true)
    public List<TicketPartView> ticketParts(UUID ticketId, UserAccount actor) {
        MaintenanceTicket t = tickets.findById(ticketId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu"));
        boolean elevated = actor.getRoles().stream()
                .anyMatch(x -> x.getName() == com.example.quanlybaotri.identity.domain.RoleName.ADMIN
                        || x.getName() == com.example.quanlybaotri.identity.domain.RoleName.MANAGER);
        if (!elevated && !t.getRequester().getId().equals(actor.getId())
                && (t.getAssignee() == null || !t.getAssignee().getId().equals(actor.getId())))
            throw ApiException.forbidden("Không có quyền xem linh kiện của phiếu");
        return usages.findByTicketIdOrderByUsedAtDesc(ticketId).stream().map(TicketPartView::from).toList();
    }

    @Transactional(readOnly = true)
    public Page<MovementView> movements(Pageable p) {
        return movements.findAll(p).map(MovementView::from);
    }

    private Part find(UUID id) {
        return parts.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy linh kiện"));
    }

    private Part locked(UUID id) {
        return parts.findLocked(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy linh kiện"));
    }

    private void positive(BigDecimal q) {
        if (q == null || q.signum() <= 0)
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_QUANTITY",
                    "Số lượng phải lớn hơn 0");
    }

    private String required(String s) {
        if (s == null || s.isBlank())
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "REASON_REQUIRED",
                    "Vui lòng nhập lý do");
        return s.trim();
    }
}
