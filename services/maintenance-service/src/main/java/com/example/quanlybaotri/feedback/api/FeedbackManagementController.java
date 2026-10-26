package com.example.quanlybaotri.feedback.api;

import com.example.quanlybaotri.feedback.application.SystemFeedbackService;
import com.example.quanlybaotri.feedback.api.SystemFeedbackController.SystemFeedbackView;
import com.example.quanlybaotri.feedback.domain.*;
import com.example.quanlybaotri.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/feedback-management")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class FeedbackManagementController {

    private final SystemFeedbackService service;
    private final CurrentUser current;

    public FeedbackManagementController(SystemFeedbackService service, CurrentUser current) {
        this.service = service;
        this.current = current;
    }

    @GetMapping("/system")
    public Page<SystemFeedbackView> system(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) SystemFeedbackStatus status,
        @RequestParam(required = false) SystemFeedbackCategory category,
        @RequestParam(required = false) UUID requesterId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        Pageable pageable
    ) {
        return service.manageSystem(q, status, category, requesterId, from, to, pageable, current.require());
    }

    @PostMapping("/system/{id}/review")
    public SystemFeedbackView review(
        @PathVariable UUID id,
        @Valid @RequestBody ReviewSystemFeedbackRequest request
    ) {
        return service.review(id, request, current.require());
    }

    @GetMapping("/tickets")
    public Page<TicketFeedbackManagementView> tickets(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) @Min(1) @Max(5) Integer rating,
        @RequestParam(required = false) UUID customerId,
        @RequestParam(required = false) UUID siteId,
        @RequestParam(required = false) UUID requesterId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        Pageable pageable
    ) {
        return service.manageTicketFeedback(
            q, rating, customerId, siteId, requesterId, from, to, pageable, current.require()
        );
    }

    public record ReviewSystemFeedbackRequest(
        @NotNull SystemFeedbackStatus status,
        @Size(max = 5000) String response
    ) {}

    public record TicketFeedbackManagementView(
        UUID ticketId,
        String ticketCode,
        String ticketTitle,
        UUID requesterId,
        String requesterName,
        UUID customerId,
        String customerName,
        UUID siteId,
        String siteName,
        Integer rating,
        String comment,
        Instant submittedAt
    ) {}
}
