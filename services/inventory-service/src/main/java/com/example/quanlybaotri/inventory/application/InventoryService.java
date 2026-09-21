package com.example.quanlybaotri.inventory.application;

import com.example.quanlybaotri.identity.client.IdentityClient;
import com.example.quanlybaotri.inventory.api.InventoryController.*;
import com.example.quanlybaotri.inventory.domain.*;
import com.example.quanlybaotri.inventory.persistence.*;
import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.shared.security.CurrentUser.Actor;
import java.math.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class InventoryService {

    private final PartRepository parts;
    private final StockMovementRepository movements;
    private final TicketPartRepository usages;
    private final InventoryEventService events;
    private final MaintenanceClient maintenance;
    private final IdentityClient identities;
    private final TransactionTemplate transactions;

    public InventoryService(
        PartRepository p,
        StockMovementRepository m,
        TicketPartRepository u,
        InventoryEventService e,
        MaintenanceClient maintenance,
        IdentityClient identities,
        TransactionTemplate transactions
    ) {
        parts = p;
        movements = m;
        usages = u;
        events = e;
        this.maintenance = maintenance;
        this.identities = identities;
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public Page<PartView> list(Pageable p) {
        return parts.findAll(p).map(PartView::from);
    }

    @Transactional
    public PartView create(PartRequest r) {
        if (parts.existsByCodeIgnoreCase(r.code())) throw ApiException.conflict(
            "Mã linh kiện đã tồn tại"
        );
        return PartView.from(
            parts.save(
                new Part(
                    r.code().trim().toUpperCase(),
                    r.name().trim(),
                    r.unit().trim(),
                    r.minimumStock(),
                    r.defaultUnitCost()
                )
            )
        );
    }

    @Transactional
    public PartView update(UUID id, PartRequest r) {
        Part p = find(id);
        p.update(
            r.code().trim().toUpperCase(),
            r.name().trim(),
            r.unit().trim(),
            r.minimumStock(),
            r.defaultUnitCost(),
            r.active()
        );
        return PartView.from(p);
    }

    @Transactional
    public PartView receive(UUID id, MovementRequest r, Actor actor) {
        Part p = locked(id);
        positive(r.quantity());
        p.add(r.quantity());
        movements.save(
            new StockMovement(
                p,
                null,
                actor.id(),
                StockMovementType.IN,
                r.quantity(),
                p.getCurrentStock(),
                r.unitCost(),
                required(r.reason())
            )
        );
        return PartView.from(p);
    }

    @Transactional
    public PartView adjust(UUID id, AdjustmentRequest r, Actor actor) {
        Part p = locked(id);
        if (r.newBalance().signum() < 0) throw ApiException.conflict("Tồn kho không thể âm");
        BigDecimal delta = r.newBalance().subtract(p.getCurrentStock());
        p.adjust(r.newBalance());
        movements.save(
            new StockMovement(
                p,
                null,
                actor.id(),
                StockMovementType.ADJUSTMENT,
                delta,
                p.getCurrentStock(),
                null,
                required(r.reason())
            )
        );
        return PartView.from(p);
    }

    public TicketPartView use(UUID ticketId, UsePartRequest r, Actor actor) {
        MaintenanceClient.TicketRef ticket = maintenance.authorize(
            ticketId,
            "USE_PART",
            actor.token()
        );
        TicketPart usage = transactions.execute(tx -> {
            positive(r.quantity());
            Part p = locked(r.partId());
            if (!p.isActive()) throw ApiException.conflict("Linh kiện đã ngừng sử dụng");
            p.issue(r.quantity());
            BigDecimal cost = (
                r.unitCost() == null ? p.getDefaultUnitCost() : r.unitCost()
            ).setScale(2, RoundingMode.HALF_UP);
            TicketPart saved = usages.save(
                new TicketPart(ticketId, p, r.quantity(), cost, actor.id())
            );
            movements.save(
                new StockMovement(
                    p,
                    ticketId,
                    actor.id(),
                    StockMovementType.OUT,
                    r.quantity().negate(),
                    p.getCurrentStock(),
                    cost,
                    "Sử dụng cho phiếu " + ticket.ticketCode()
                )
            );
            Map<String, Object> used = new LinkedHashMap<>();
            used.put("ticketId", ticketId);
            used.put("ticketCode", ticket.ticketCode());
            used.put("partId", p.getId());
            used.put("partCode", p.getCode());
            used.put("partName", p.getName());
            used.put("quantity", r.quantity());
            used.put("unitCost", cost);
            used.put("usedById", actor.id());
            events.publish("inventory.part.used", saved.getId(), used);
            if (p.getCurrentStock().compareTo(p.getMinimumStock()) <= 0) events.publish(
                "inventory.part.low-stock",
                p.getId(),
                Map.of(
                    "partId",
                    p.getId(),
                    "partCode",
                    p.getCode(),
                    "partName",
                    p.getName(),
                    "balance",
                    p.getCurrentStock(),
                    "minimumStock",
                    p.getMinimumStock(),
                    "recipientRole",
                    "MANAGER"
                )
            );
            return saved;
        });
        return TicketPartView.from(
            usage,
            new IdentityClient.UserRef(
                actor.id(),
                actor.username(),
                actor.fullName(),
                true,
                actor.roles()
            )
        );
    }

    public List<TicketPartView> ticketParts(UUID ticketId, Actor actor) {
        maintenance.authorize(ticketId, "VIEW_PARTS", actor.token());
        List<TicketPart> result = usages.findByTicketIdOrderByUsedAtDesc(ticketId);
        Map<UUID, IdentityClient.UserRef> users = identities.resolve(
            result.stream().map(TicketPart::getUsedById).toList()
        );
        return result
            .stream()
            .map(p -> TicketPartView.from(p, users.get(p.getUsedById())))
            .toList();
    }

    public Page<MovementView> movements(Pageable pageable) {
        Page<StockMovement> page = movements.findAll(pageable);
        Map<UUID, IdentityClient.UserRef> users = identities.resolve(
            page.getContent().stream().map(StockMovement::getActorId).toList()
        );
        Map<UUID, MaintenanceClient.TicketRef> tickets = maintenance.resolve(
            page
                .getContent()
                .stream()
                .map(StockMovement::getTicketId)
                .filter(Objects::nonNull)
                .toList()
        );
        return page.map(m ->
            MovementView.from(
                m,
                users.get(m.getActorId()),
                m.getTicketId() == null ? null : tickets.get(m.getTicketId())
            )
        );
    }

    private Part find(UUID id) {
        return parts
            .findById(id)
            .orElseThrow(() -> ApiException.notFound("Không tìm thấy linh kiện"));
    }

    private Part locked(UUID id) {
        return parts
            .findLocked(id)
            .orElseThrow(() -> ApiException.notFound("Không tìm thấy linh kiện"));
    }

    private void positive(BigDecimal q) {
        if (q == null || q.signum() <= 0) throw new ApiException(
            org.springframework.http.HttpStatus.BAD_REQUEST,
            "INVALID_QUANTITY",
            "Số lượng phải lớn hơn 0"
        );
    }

    private String required(String s) {
        if (s == null || s.isBlank()) throw new ApiException(
            org.springframework.http.HttpStatus.BAD_REQUEST,
            "REASON_REQUIRED",
            "Vui lòng nhập lý do"
        );
        return s.trim();
    }
}
