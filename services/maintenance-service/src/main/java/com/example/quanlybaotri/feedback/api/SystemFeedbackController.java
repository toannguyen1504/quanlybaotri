package com.example.quanlybaotri.feedback.api;

import com.example.quanlybaotri.feedback.application.SystemFeedbackService;
import com.example.quanlybaotri.feedback.domain.*;
import com.example.quanlybaotri.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/system-feedback")
public class SystemFeedbackController {

    private final SystemFeedbackService service;
    private final CurrentUser current;

    public SystemFeedbackController(SystemFeedbackService service, CurrentUser current) {
        this.service = service;
        this.current = current;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('REQUESTER')")
    public SystemFeedbackView create(@Valid @RequestBody CreateSystemFeedbackRequest request) {
        return service.create(request, current.require());
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('REQUESTER')")
    public Page<SystemFeedbackView> mine(Pageable pageable) {
        return service.mine(current.require(), pageable);
    }

    @GetMapping("/{id}")
    public SystemFeedbackView get(@PathVariable UUID id) {
        return service.get(id, current.require());
    }

    public record CreateSystemFeedbackRequest(
        @NotNull SystemFeedbackCategory category,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 5000) String content
    ) {}

    public record SystemFeedbackView(
        UUID id,
        UUID requesterId,
        String requesterName,
        SystemFeedbackCategory category,
        String title,
        String content,
        SystemFeedbackStatus status,
        String response,
        UUID reviewedById,
        String reviewedByName,
        Instant reviewedAt,
        Instant createdAt,
        Instant updatedAt,
        long version
    ) {}
}
