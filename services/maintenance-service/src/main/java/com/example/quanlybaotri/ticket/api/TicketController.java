package com.example.quanlybaotri.ticket.api;

import com.example.quanlybaotri.equipment.client.AssetClient;
import com.example.quanlybaotri.identity.client.IdentityClient;
import com.example.quanlybaotri.shared.security.CurrentUser;
import com.example.quanlybaotri.ticket.application.TicketExcelExporter;
import com.example.quanlybaotri.ticket.application.TicketService;
import com.example.quanlybaotri.ticket.domain.*;
import com.example.quanlybaotri.cost.domain.CostStatus;
import com.example.quanlybaotri.cost.domain.TicketCostSummary;
import com.example.quanlybaotri.organization.client.OrganizationClient;
import com.example.quanlybaotri.payment.client.PaymentClient.PaymentSnapshot;
import com.example.quanlybaotri.payment.client.TicketPaymentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {

    private final TicketService service;
    private final CurrentUser current;
    private final TicketExcelExporter excelExporter;

    public TicketController(
        TicketService service,
        CurrentUser current,
        TicketExcelExporter excelExporter
    ) {
        this.service = service;
        this.current = current;
        this.excelExporter = excelExporter;
    }

    @GetMapping
    public Page<TicketView> list(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) TicketStatus status,
        @RequestParam(required = false) TicketPriority priority,
        @RequestParam(required = false) UUID customerId,
        @RequestParam(required = false) UUID siteId,
        @RequestParam(required = false) UUID equipmentId,
        @RequestParam(required = false) UUID equipmentCategoryId,
        @RequestParam(required = false) UUID technicianId,
        @RequestParam(required = false) WarrantyStatus warrantyStatus,
        @RequestParam(required = false) TicketWorkQueue queue,
        @RequestParam(defaultValue = "ALL") TicketListView view,
        Pageable pageable
    ) {
        return service.list(current.require(), q, status, priority, customerId, siteId,
            equipmentId, equipmentCategoryId, technicianId, warrantyStatus, queue, view, pageable);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) TicketStatus status,
        @RequestParam(required = false) TicketPriority priority,
        @RequestParam(required = false) UUID customerId,
        @RequestParam(required = false) UUID siteId,
        @RequestParam(required = false) UUID equipmentId,
        @RequestParam(required = false) UUID equipmentCategoryId,
        @RequestParam(required = false) UUID technicianId,
        @RequestParam(required = false) WarrantyStatus warrantyStatus,
        @RequestParam(required = false) TicketWorkQueue queue,
        @RequestParam(defaultValue = "ALL") TicketListView view
    ) {
        Instant generatedAt = Instant.now();
        List<TicketView> tickets = service.export(
            current.require(),
            q,
            status,
            priority,
            customerId,
            siteId,
            equipmentId,
            equipmentCategoryId,
            technicianId,
            warrantyStatus,
            queue,
            view
        );
        byte[] content = excelExporter.export(tickets, generatedAt);
        String filename =
            "phieu-bao-tri-" +
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                .format(generatedAt) +
            ".xlsx";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(
            MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            )
        );
        headers.setContentDisposition(
            ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build()
        );
        headers.setContentLength(content.length);
        return new ResponseEntity<>(content, headers, HttpStatus.OK);
    }

    @GetMapping("/summary")
    public TicketSummaryView summary() {
        return service.summary(current.require());
    }

    @GetMapping("/{id}")
    public TicketView get(@PathVariable UUID id) {
        return service.get(id, current.require());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('REQUESTER','ADMIN','MANAGER')")
    public TicketView create(
        @Valid @RequestBody CreateTicketRequest r,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return service.create(r, current.require(), idempotencyKey);
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public TicketView accept(@PathVariable UUID id) {
        return service.accept(id, current.require());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public TicketView reject(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r) {
        return service.reject(id, r.reason(), current.require());
    }

    @PostMapping("/{id}/cancel")
    public TicketView cancel(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r) {
        return service.cancel(id, r.reason(), current.require());
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public TicketView assign(@PathVariable UUID id, @Valid @RequestBody AssignRequest r) {
        return service.assign(id, r.technicianId(), r.reason(), r.outsideRegionConfirmed(), current.require());
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public TicketView start(@PathVariable UUID id) {
        return service.start(id, current.require());
    }

    @PostMapping("/{id}/wait-parts")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public TicketView waitParts(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r) {
        return service.waitParts(id, r.reason(), current.require());
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public TicketView resume(@PathVariable UUID id) {
        return service.resume(id, current.require());
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public TicketView resolve(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r) {
        return service.resolve(id, r.reason(), current.require());
    }

    @PostMapping("/{id}/reopen")
    public TicketView reopen(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r) {
        return service.reopen(id, r.reason(), current.require());
    }

    @PostMapping("/{id}/close")
    public TicketView close(
        @PathVariable UUID id,
        @Valid @RequestBody(required = false) CloseTicketRequest r
    ) {
        return service.close(
            id,
            r == null ? null : r.reason(),
            r == null ? null : r.feedback(),
            current.require()
        );
    }

    @PostMapping("/{id}/priority")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public TicketView priority(@PathVariable UUID id, @Valid @RequestBody PriorityRequest r) {
        return service.changePriority(id, r.priority(), current.require());
    }

    @PostMapping("/{id}/work-logs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TECHNICIAN')")
    public WorkLogView work(@PathVariable UUID id, @Valid @RequestBody WorkLogRequest r) {
        return service.addWorkLog(id, r.content(), r.minutesSpent(), current.require());
    }

    @GetMapping("/{id}/work-logs")
    public List<WorkLogView> workLogs(@PathVariable UUID id) {
        return service.workLogs(id, current.require());
    }

    @GetMapping("/{id}/timeline")
    public List<EventView> timeline(@PathVariable UUID id) {
        return service.timeline(id, current.require());
    }

    public record CreateTicketRequest(
        @NotNull UUID equipmentId,
        UUID requesterId,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 10000) String description,
        @NotNull TicketPriority priority
    ) {}

    public record ReasonRequest(@NotBlank @Size(max = 5000) String reason) {}

    public record CloseTicketRequest(
        @Size(max = 5000) String reason,
        @Valid FeedbackRequest feedback
    ) {}

    public record FeedbackRequest(
        @Min(1) @Max(5) Integer rating,
        @Size(max = 5000) String comment
    ) {
        public boolean provided() {
            return rating != null || (comment != null && !comment.isBlank());
        }
    }

    public record AssignRequest(@NotNull UUID technicianId, @Size(max = 500) String reason, boolean outsideRegionConfirmed) {}

    public record PriorityRequest(@NotNull TicketPriority priority) {}

    public record WorkLogRequest(
        @NotBlank @Size(max = 10000) String content,
        @Min(0) @Max(1440) int minutesSpent
    ) {}

    public record TicketSummaryView(long total, long open, long dueSoon, long overdue) {}

    public record TicketFeedbackView(Integer rating, String comment, Instant submittedAt) {
        public static TicketFeedbackView from(MaintenanceTicket ticket) {
            if (ticket.getFeedbackSubmittedAt() == null) return null;
            return new TicketFeedbackView(
                ticket.getFeedbackRating(),
                ticket.getFeedbackComment(),
                ticket.getFeedbackSubmittedAt()
            );
        }
    }

    public record TicketView(
        UUID id,
        String code,
        UUID equipmentId,
        String equipmentCode,
        String equipmentName,
        UUID customerId,
        String customerName,
        UUID siteId,
        String siteName,
        UUID regionId,
        String regionName,
        UUID requesterId,
        String requesterName,
        UUID createdById,
        String createdByName,
        UUID assigneeId,
        String assigneeName,
        String title,
        String description,
        TicketPriority priority,
        TicketStatus status,
        Instant submittedAt,
        Instant responseDueAt,
        Instant resolutionDueAt,
        Instant acceptedAt,
        Instant startedAt,
        Instant resolvedAt,
        Instant closedAt,
        String resolutionSummary,
        TicketFeedbackView feedback,
        WarrantyStatus warrantyStatusAtCreation,
        Instant warrantyEndAtCreation,
        boolean costVisible,
        CostStatus costStatus,
        BigDecimal partsTotal,
        BigDecimal otherTotal,
        BigDecimal grandTotal,
        TicketPaymentStatus paymentStatus,
        BigDecimal amountDue,
        Instant paidAt,
        long version
    ) {
        public static TicketView from(
            MaintenanceTicket t,
            AssetClient.EquipmentRef equipment,
            Map<UUID, IdentityClient.UserRef> users,
            OrganizationClient.DepartmentRef department,
            TicketCostSummary costs,
            boolean costVisible,
            PaymentSnapshot payment
        ) {
            IdentityClient.UserRef requester = users.get(t.getRequesterId()),
                createdBy = users.get(t.getCreatedById()),
                assignee = t.getAssigneeId() == null ? null : users.get(t.getAssigneeId());
            return new TicketView(
                t.getId(),
                t.getCode(),
                t.getEquipmentId(),
                equipment == null ? null : equipment.assetCode(),
                equipment == null ? null : equipment.name(),
                t.getCustomerId(),
                department == null ? null : department.customerName(),
                t.getSiteId(),
                department == null ? null : department.name(),
                department == null ? null : department.regionId(),
                department == null ? null : department.regionName(),
                t.getRequesterId(),
                requester == null ? null : requester.fullName(),
                t.getCreatedById(),
                createdBy == null ? null : createdBy.fullName(),
                t.getAssigneeId(),
                assignee == null ? null : assignee.fullName(),
                t.getTitle(),
                t.getDescription(),
                t.getPriority(),
                t.getStatus(),
                t.getSubmittedAt(),
                t.getResponseDueAt(),
                t.getResolutionDueAt(),
                t.getAcceptedAt(),
                t.getStartedAt(),
                t.getResolvedAt(),
                t.getClosedAt(),
                t.getResolutionSummary(),
                TicketFeedbackView.from(t),
                t.getWarrantyStatusAtCreation(),
                t.getWarrantyEndAtCreation(),
                costVisible,
                costVisible ? (costs == null ? CostStatus.DRAFT : costs.getStatus()) : null,
                costVisible ? (costs == null ? BigDecimal.ZERO : costs.getPartsTotal()) : null,
                costVisible ? (costs == null ? BigDecimal.ZERO : costs.getOtherTotal()) : null,
                costVisible ? (costs == null ? BigDecimal.ZERO : costs.getGrandTotal()) : null,
                payment == null ? TicketPaymentStatus.NOT_AVAILABLE : payment.status(),
                payment == null ? null : payment.amountDue(),
                payment == null ? null : payment.paidAt(),
                t.getVersion()
            );
        }
    }

    public record EventView(
        UUID id,
        String type,
        TicketStatus fromStatus,
        TicketStatus toStatus,
        String description,
        String metadataJson,
        UUID actorId,
        String actorName,
        Instant createdAt
    ) {
        public static EventView from(TicketEvent e, IdentityClient.UserRef actor) {
            return new EventView(
                e.getId(),
                e.getEventType(),
                e.getFromStatus(),
                e.getToStatus(),
                e.getDescription(),
                e.getMetadataJson(),
                e.getActorId(),
                actor == null ? "Hệ thống" : actor.fullName(),
                e.getCreatedAt()
            );
        }
    }

    public record WorkLogView(
        UUID id,
        UUID technicianId,
        String technicianName,
        String content,
        int minutesSpent,
        Instant createdAt
    ) {
        public static WorkLogView from(WorkLog w, IdentityClient.UserRef technician) {
            return new WorkLogView(
                w.getId(),
                w.getTechnicianId(),
                technician == null ? null : technician.fullName(),
                w.getContent(),
                w.getMinutesSpent(),
                w.getCreatedAt()
            );
        }
    }
}
