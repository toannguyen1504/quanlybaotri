package com.example.quanlybaotri.telegram.api;

import com.example.quanlybaotri.cost.api.CostController.*;
import com.example.quanlybaotri.cost.domain.CostCategory;
import com.example.quanlybaotri.telegram.application.TelegramWorkflowService;
import com.example.quanlybaotri.ticket.api.AttachmentController.AttachmentView;
import com.example.quanlybaotri.ticket.api.TicketController.*;
import com.example.quanlybaotri.ticket.api.InternalTicketController.Authorization;
import com.example.quanlybaotri.ticket.domain.AttachmentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/internal/v1/telegram")
@PreAuthorize("authentication.name == 'notification-service'")
public class TelegramWorkflowController {
    private final TelegramWorkflowService workflow;

    public TelegramWorkflowController(TelegramWorkflowService workflow) {
        this.workflow = workflow;
    }

    @GetMapping("/tickets")
    public Page<TicketView> tickets(
        @RequestParam UUID technicianId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "5") int size
    ) { return workflow.list(technicianId, page, size); }

    @GetMapping("/tickets/{ticketId}")
    public TicketView ticket(@PathVariable UUID ticketId, @RequestParam UUID technicianId) {
        return workflow.get(ticketId, technicianId);
    }

    @GetMapping("/tickets/{ticketId}/inventory-access")
    @PreAuthorize("authentication.name == 'notification-service' or authentication.name == 'inventory-service'")
    public Authorization inventoryAccess(
        @PathVariable UUID ticketId,
        @RequestParam UUID technicianId
    ) { return workflow.authorizeInventory(ticketId, technicianId); }

    @GetMapping("/tickets/{ticketId}/costs")
    public CostView costs(@PathVariable UUID ticketId, @RequestParam UUID technicianId) {
        return workflow.costs(ticketId, technicianId);
    }

    @GetMapping("/tickets/{ticketId}/work-logs")
    public List<WorkLogView> workLogs(@PathVariable UUID ticketId, @RequestParam UUID technicianId) {
        return workflow.workLogs(ticketId, technicianId);
    }

    @GetMapping("/tickets/{ticketId}/attachments")
    public List<AttachmentView> attachments(@PathVariable UUID ticketId, @RequestParam UUID technicianId) {
        return workflow.attachments(ticketId, technicianId);
    }

    @PostMapping("/tickets/{ticketId}/{action:start|resume}")
    public TicketView simple(
        @PathVariable UUID ticketId,
        @PathVariable String action,
        @Valid @RequestBody TechnicianRequest request,
        @RequestHeader("Idempotency-Key") String key
    ) {
        return "start".equals(action)
            ? workflow.start(ticketId, request.technicianId(), key)
            : workflow.resume(ticketId, request.technicianId(), key);
    }

    @PostMapping("/tickets/{ticketId}/{action:wait-parts|resolve}")
    public TicketView reason(
        @PathVariable UUID ticketId,
        @PathVariable String action,
        @Valid @RequestBody ReasonRequest request,
        @RequestHeader("Idempotency-Key") String key
    ) {
        return "wait-parts".equals(action)
            ? workflow.waitParts(ticketId, request.technicianId(), request.reason(), key)
            : workflow.resolve(ticketId, request.technicianId(), request.reason(), key);
    }

    @PostMapping("/tickets/{ticketId}/work-logs")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkLogView workLog(
        @PathVariable UUID ticketId,
        @Valid @RequestBody TelegramWorkLogRequest request,
        @RequestHeader("Idempotency-Key") String key
    ) {
        return workflow.addWorkLog(ticketId, request.technicianId(), request.content(), request.minutesSpent(), key);
    }

    @PostMapping("/tickets/{ticketId}/cost-items")
    @ResponseStatus(HttpStatus.CREATED)
    public CostItemView addCost(
        @PathVariable UUID ticketId,
        @Valid @RequestBody TelegramCostRequest request,
        @RequestHeader("Idempotency-Key") String key
    ) {
        return workflow.addCost(ticketId, request.technicianId(), request.toCostRequest(), key);
    }

    @PutMapping("/tickets/{ticketId}/cost-items/{itemId}")
    public CostItemView updateCost(
        @PathVariable UUID ticketId,
        @PathVariable UUID itemId,
        @Valid @RequestBody TelegramCostRequest request,
        @RequestHeader("Idempotency-Key") String key
    ) {
        return workflow.updateCost(ticketId, itemId, request.technicianId(), request.toCostRequest(), key);
    }

    @DeleteMapping("/tickets/{ticketId}/cost-items/{itemId}")
    public CostView deleteCost(
        @PathVariable UUID ticketId,
        @PathVariable UUID itemId,
        @RequestParam UUID technicianId,
        @RequestHeader("Idempotency-Key") String key
    ) { return workflow.deleteCost(ticketId, itemId, technicianId, key); }

    @PostMapping("/tickets/{ticketId}/costs/quote")
    public CostView quoteCosts(
        @PathVariable UUID ticketId,
        @Valid @RequestBody TechnicianRequest request,
        @RequestHeader("Idempotency-Key") String key
    ) { return workflow.quoteCosts(ticketId, request.technicianId(), key); }

    @PostMapping(value = "/tickets/{ticketId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentView upload(
        @PathVariable UUID ticketId,
        @RequestParam UUID technicianId,
        @RequestParam AttachmentType type,
        @RequestParam(required = false) UUID costItemId,
        @RequestPart("file") MultipartFile file,
        @RequestHeader("Idempotency-Key") String key
    ) { return workflow.upload(ticketId, technicianId, costItemId, type, file, key); }

    @DeleteMapping("/tickets/{ticketId}/attachments/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttachment(
        @PathVariable UUID ticketId,
        @PathVariable UUID attachmentId,
        @RequestParam UUID technicianId,
        @RequestHeader("Idempotency-Key") String key
    ) { workflow.deleteAttachment(ticketId, attachmentId, technicianId, key); }

    public record TechnicianRequest(@NotNull UUID technicianId) {}
    public record ReasonRequest(@NotNull UUID technicianId, @NotBlank @Size(max = 5000) String reason) {}
    public record TelegramWorkLogRequest(
        @NotNull UUID technicianId,
        @NotBlank @Size(max = 10000) String content,
        @Min(0) @Max(1440) int minutesSpent
    ) {}
    public record TelegramCostRequest(
        @NotNull UUID technicianId,
        @NotNull CostCategory category,
        @NotBlank @Size(max = 500) String description,
        @NotNull @DecimalMin("0.001") BigDecimal quantity,
        @NotBlank @Size(max = 30) String unit,
        @NotNull @DecimalMin("0") BigDecimal unitCost,
        Instant incurredAt,
        @Size(max = 200) String vendor,
        @Size(max = 100) String referenceNumber
    ) {
        ManualCostRequest toCostRequest() {
            return new ManualCostRequest(category, description, quantity, unit, unitCost, incurredAt, vendor, referenceNumber);
        }
    }
}
