package com.example.quanlybaotri.inventory.api;

import com.example.quanlybaotri.identity.client.IdentityClient;
import com.example.quanlybaotri.identity.domain.RoleName;
import com.example.quanlybaotri.inventory.application.InventoryService;
import com.example.quanlybaotri.inventory.application.MaintenanceClient;
import com.example.quanlybaotri.inventory.domain.*;
import com.example.quanlybaotri.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class InventoryController {

    private final InventoryService service;
    private final CurrentUser current;

    public InventoryController(InventoryService s, CurrentUser c) {
        service = s;
        current = c;
    }

    @GetMapping("/parts")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','TECHNICIAN')")
    public Page<PartView> parts(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String stockStatus,
        @RequestParam(required = false) Boolean active,
        Pageable p
    ) {
        return service.list(q, stockStatus, active, p);
    }

    @GetMapping("/parts/summary")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','TECHNICIAN')")
    public PartSummary partSummary(
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate end = to == null ? LocalDate.now(zone) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end)) throw new com.example.quanlybaotri.shared.api.ApiException(
            org.springframework.http.HttpStatus.BAD_REQUEST,
            "INVALID_INVENTORY_DATE_RANGE",
            "NgÃ y báº¯t Ä‘áº§u khÃ´ng Ä‘Æ°á»£c sau ngÃ y káº¿t thÃºc"
        );
        PartSummary summary = service.summary(
            start.atStartOfDay(zone).toInstant(),
            end.plusDays(1).atStartOfDay(zone).toInstant()
        );
        var actor = current.require();
        if (actor.has(RoleName.ADMIN) || actor.has(RoleName.MANAGER)) return summary;
        return new PartSummary(
            summary.total(), summary.active(), summary.lowStock(), summary.outOfStock(),
            summary.inventoryValue(), null, null, null
        );
    }

    @PostMapping("/parts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public PartView create(@Valid @RequestBody PartRequest r) {
        return service.create(r);
    }

    @PutMapping("/parts/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public PartView update(@PathVariable UUID id, @Valid @RequestBody PartRequest r) {
        return service.update(id, r);
    }

    @PostMapping("/parts/{id}/receive")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public PartView receive(@PathVariable UUID id, @Valid @RequestBody MovementRequest r) {
        return service.receive(id, r, current.require());
    }

    @PostMapping("/parts/{id}/adjust")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public PartView adjust(@PathVariable UUID id, @Valid @RequestBody AdjustmentRequest r) {
        return service.adjust(id, r, current.require());
    }

    @GetMapping("/stock-movements")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Page<MovementView> movements(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) UUID partId,
        @RequestParam(required = false) StockMovementType type,
        @RequestParam(required = false) UUID ticketId,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate to,
        Pageable p
    ) {
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        if (from != null && to != null && from.isAfter(to)) throw new com.example.quanlybaotri.shared.api.ApiException(
            org.springframework.http.HttpStatus.BAD_REQUEST,
            "INVALID_INVENTORY_DATE_RANGE",
            "NgÃ y báº¯t Ä‘áº§u khÃ´ng Ä‘Æ°á»£c sau ngÃ y káº¿t thÃºc"
        );
        return service.movements(
            q,
            partId,
            type,
            ticketId,
            from == null ? null : from.atStartOfDay(zone).toInstant(),
            to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant(),
            p
        );
    }

    @PostMapping("/tickets/{ticketId}/parts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TECHNICIAN')")
    public TicketPartView use(@PathVariable UUID ticketId, @Valid @RequestBody UsePartRequest r) {
        return service.use(ticketId, r, current.require());
    }

    @GetMapping("/tickets/{ticketId}/parts")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','TECHNICIAN')")
    public List<TicketPartView> ticketParts(@PathVariable UUID ticketId) {
        return service.ticketParts(ticketId, current.require());
    }

    public record PartRequest(
        @NotBlank @Size(max = 80) String code,
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 50) String unit,
        @NotNull @DecimalMin("0") BigDecimal minimumStock,
        @NotNull @DecimalMin("0") BigDecimal defaultUnitCost,
        boolean active
    ) {}

    public record MovementRequest(
        @NotNull @DecimalMin("0.001") BigDecimal quantity,
        @DecimalMin("0") BigDecimal unitCost,
        @NotBlank @Size(max = 500) String reason
    ) {}

    public record AdjustmentRequest(
        @NotNull @DecimalMin("0") BigDecimal newBalance,
        @NotBlank @Size(max = 500) String reason
    ) {}

    public record UsePartRequest(
        @NotNull UUID partId,
        @NotNull @DecimalMin("0.001") BigDecimal quantity,
        @DecimalMin("0") BigDecimal unitCost
    ) {}

    public record PartView(
        UUID id,
        String code,
        String name,
        String unit,
        BigDecimal currentStock,
        BigDecimal minimumStock,
        BigDecimal defaultUnitCost,
        boolean active,
        long version
    ) {
        public static PartView from(Part p) {
            return new PartView(
                p.getId(),
                p.getCode(),
                p.getName(),
                p.getUnit(),
                p.getCurrentStock(),
                p.getMinimumStock(),
                p.getDefaultUnitCost(),
                p.isActive(),
                p.getVersion()
            );
        }
    }

    public record PartSummary(
        long total,
        long active,
        long lowStock,
        long outOfStock,
        BigDecimal inventoryValue,
        BigDecimal receivedQuantity,
        BigDecimal issuedQuantity,
        BigDecimal adjustedQuantity
    ) {}

    public record TicketPartView(
        UUID id,
        UUID partId,
        String partCode,
        String partName,
        BigDecimal quantity,
        BigDecimal unitCost,
        UUID usedById,
        String usedByName,
        Instant usedAt
    ) {
        public static TicketPartView from(TicketPart p, IdentityClient.UserRef user) {
            return new TicketPartView(
                p.getId(),
                p.getPart().getId(),
                p.getPart().getCode(),
                p.getPart().getName(),
                p.getQuantity(),
                p.getUnitCost(),
                p.getUsedById(),
                user == null ? null : user.fullName(),
                p.getUsedAt()
            );
        }
    }

    public record MovementView(
        UUID id,
        UUID partId,
        String partCode,
        UUID ticketId,
        String ticketCode,
        StockMovementType type,
        BigDecimal quantity,
        BigDecimal balanceAfter,
        BigDecimal unitCost,
        String reason,
        String actorName,
        Instant createdAt
    ) {
        public static MovementView from(
            StockMovement m,
            IdentityClient.UserRef actor,
            MaintenanceClient.TicketRef ticket
        ) {
            return new MovementView(
                m.getId(),
                m.getPart().getId(),
                m.getPart().getCode(),
                m.getTicketId(),
                ticket == null ? null : ticket.ticketCode(),
                m.getType(),
                m.getQuantity(),
                m.getBalanceAfter(),
                m.getUnitCost(),
                m.getReason(),
                actor == null ? null : actor.fullName(),
                m.getCreatedAt()
            );
        }
    }
}
