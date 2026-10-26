package com.example.quanlybaotri.telegram.api;

import com.example.quanlybaotri.inventory.api.InventoryController.PartView;
import com.example.quanlybaotri.inventory.api.InventoryController.TicketPartView;
import com.example.quanlybaotri.telegram.application.TelegramInventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/telegram")
@PreAuthorize("authentication.name == 'notification-service'")
public class TelegramInventoryController {
    private final TelegramInventoryService inventory;

    public TelegramInventoryController(TelegramInventoryService inventory) {
        this.inventory = inventory;
    }

    @GetMapping("/parts")
    public Page<PartView> search(
        @RequestParam(defaultValue = "") String q,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "5") int size
    ) { return inventory.search(q, page, size); }

    @GetMapping("/tickets/{ticketId}/parts")
    public List<TicketPartView> used(
        @PathVariable UUID ticketId,
        @RequestParam UUID technicianId
    ) { return inventory.used(ticketId, technicianId); }

    @PostMapping("/tickets/{ticketId}/parts")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketPartView use(
        @PathVariable UUID ticketId,
        @Valid @RequestBody UseRequest request,
        @RequestHeader("Idempotency-Key") String key
    ) { return inventory.use(ticketId, request.technicianId(), request.partId(), request.quantity(), key); }

    public record UseRequest(
        @NotNull UUID technicianId,
        @NotNull UUID partId,
        @NotNull @DecimalMin("0.001") BigDecimal quantity
    ) {}
}
