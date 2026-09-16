package com.example.quanlybaotri.inventory.api;

import com.example.quanlybaotri.inventory.application.InventoryService;
import com.example.quanlybaotri.inventory.domain.*;
import com.example.quanlybaotri.shared.security.CurrentUser;
import com.example.quanlybaotri.identity.client.IdentityClient;
import com.example.quanlybaotri.inventory.application.MaintenanceClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
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
    public Page<PartView> parts(Pageable p) {
        return service.list(p);
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
    public Page<MovementView> movements(Pageable p) {
        return service.movements(p);
    }

    @PostMapping("/tickets/{ticketId}/parts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TECHNICIAN')")
    public TicketPartView use(@PathVariable UUID ticketId, @Valid @RequestBody UsePartRequest r) {
        return service.use(ticketId, r, current.require());
    }

    @GetMapping("/tickets/{ticketId}/parts")
    public List<TicketPartView> ticketParts(@PathVariable UUID ticketId) {
        return service.ticketParts(ticketId, current.require());
    }

    public record PartRequest(@NotBlank @Size(max = 80) String code, @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 50) String unit, @NotNull @DecimalMin("0") BigDecimal minimumStock,
            @NotNull @DecimalMin("0") BigDecimal defaultUnitCost, boolean active) {
    }

    public record MovementRequest(@NotNull @DecimalMin("0.001") BigDecimal quantity,
            @DecimalMin("0") BigDecimal unitCost, @NotBlank @Size(max = 500) String reason) {
    }

    public record AdjustmentRequest(@NotNull @DecimalMin("0") BigDecimal newBalance,
            @NotBlank @Size(max = 500) String reason) {
    }

    public record UsePartRequest(@NotNull UUID partId, @NotNull @DecimalMin("0.001") BigDecimal quantity,
            @DecimalMin("0") BigDecimal unitCost) {
    }

    public record PartView(UUID id, String code, String name, String unit, BigDecimal currentStock,
            BigDecimal minimumStock, BigDecimal defaultUnitCost, boolean active, long version) {
        public static PartView from(Part p) {
            return new PartView(p.getId(), p.getCode(), p.getName(), p.getUnit(), p.getCurrentStock(),
                    p.getMinimumStock(), p.getDefaultUnitCost(), p.isActive(), p.getVersion());
        }
    }

    public record TicketPartView(UUID id, UUID partId, String partCode, String partName, BigDecimal quantity,
            BigDecimal unitCost, UUID usedById, String usedByName, Instant usedAt) {
        public static TicketPartView from(TicketPart p, IdentityClient.UserRef user) {
            return new TicketPartView(p.getId(), p.getPart().getId(), p.getPart().getCode(), p.getPart().getName(),
                    p.getQuantity(), p.getUnitCost(), p.getUsedById(), user == null ? null : user.fullName(),
                    p.getUsedAt());
        }
    }

    public record MovementView(UUID id, UUID partId, String partCode, UUID ticketId, String ticketCode,
            StockMovementType type, BigDecimal quantity, BigDecimal balanceAfter, BigDecimal unitCost, String reason,
            String actorName, Instant createdAt) {
        public static MovementView from(StockMovement m, IdentityClient.UserRef actor,
                MaintenanceClient.TicketRef ticket) {
            return new MovementView(m.getId(), m.getPart().getId(), m.getPart().getCode(),
                    m.getTicketId(), ticket == null ? null : ticket.ticketCode(), m.getType(), m.getQuantity(),
                    m.getBalanceAfter(), m.getUnitCost(), m.getReason(), actor == null ? null : actor.fullName(), m.getCreatedAt());
        }
    }
}
