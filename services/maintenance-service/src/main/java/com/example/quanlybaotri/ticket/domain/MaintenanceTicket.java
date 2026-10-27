package com.example.quanlybaotri.ticket.domain;

import com.example.quanlybaotri.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Formula;

@Entity
@Table(name = "tickets")
public class MaintenanceTicket extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "created_by_id", nullable = false)
    private UUID createdById;

    @Enumerated(EnumType.STRING)
    @Column(name = "warranty_status_at_creation", nullable = false, length = 30)
    private WarrantyStatus warrantyStatusAtCreation;

    @Column(name = "warranty_end_at_creation")
    private Instant warrantyEndAtCreation;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TicketStatus status;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "response_due_at", nullable = false)
    private Instant responseDueAt;

    @Column(name = "resolution_due_at", nullable = false)
    private Instant resolutionDueAt;

    @Formula("submitted_at + (resolution_due_at - submitted_at) * 0.8")
    private Instant resolutionWarningAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "resolution_summary", columnDefinition = "text")
    private String resolutionSummary;

    @Column(name = "feedback_rating")
    private Integer feedbackRating;

    @Column(name = "feedback_comment", columnDefinition = "text")
    private String feedbackComment;

    @Column(name = "feedback_submitted_at")
    private Instant feedbackSubmittedAt;

    @Column(name = "creation_idempotency_key", unique = true, length = 128)
    private String creationIdempotencyKey;

    @Column(name = "creation_actor_id")
    private UUID creationActorId;

    @Column(name = "creation_request_hash", length = 64)
    private String creationRequestHash;

    protected MaintenanceTicket() {}

    public MaintenanceTicket(
        String code,
        UUID equipmentId,
        UUID requesterId,
        String title,
        String description,
        TicketPriority priority,
        Instant responseDue,
        Instant resolutionDue
    ) {
        this(code, equipmentId, requesterId, requesterId,
            UUID.fromString("20000000-0000-0000-0000-000000000001"),
            UUID.fromString("20000000-0000-0000-0000-000000000002"),
            title, description, priority, responseDue, resolutionDue, WarrantyStatus.UNKNOWN, null);
    }

    public MaintenanceTicket(
        String code,
        UUID equipmentId,
        UUID requesterId,
        UUID customerId,
        UUID siteId,
        String title,
        String description,
        TicketPriority priority,
        Instant responseDue,
        Instant resolutionDue
    ) {
        this(code,equipmentId,requesterId,requesterId,customerId,siteId,title,description,priority,responseDue,resolutionDue,WarrantyStatus.UNKNOWN,null);
    }

    public MaintenanceTicket(
        String code,
        UUID equipmentId,
        UUID requesterId,
        UUID createdById,
        UUID customerId,
        UUID siteId,
        String title,
        String description,
        TicketPriority priority,
        Instant responseDue,
        Instant resolutionDue,
        WarrantyStatus warrantyStatus,
        Instant warrantyEndAt
    ) {
        this.code = code;
        this.equipmentId = equipmentId;
        this.requesterId = requesterId;
        this.createdById = createdById;
        this.customerId = customerId;
        this.siteId = siteId;
        this.warrantyStatusAtCreation = warrantyStatus;
        this.warrantyEndAtCreation = warrantyEndAt;
        this.title = title;
        this.description = description;
        this.priority = priority;
        status = TicketStatus.SUBMITTED;
        submittedAt = Instant.now();
        responseDueAt = responseDue;
        resolutionDueAt = resolutionDue;
    }

    public void priority(TicketPriority p) {
        priority = p;
    }

    public void creationRequest(String key, UUID actorId, String requestHash) {
        creationIdempotencyKey = key;
        creationActorId = actorId;
        creationRequestHash = requestHash;
    }

    public void updateSlaDeadlines(Instant r, Instant d) {
        responseDueAt = r;
        resolutionDueAt = d;
    }

    public void accept() {
        status = TicketStatus.ACCEPTED;
        acceptedAt = Instant.now();
    }

    public void reject() {
        status = TicketStatus.REJECTED;
    }

    public void cancel() {
        status = TicketStatus.CANCELLED;
    }

    public void assign(UUID id) {
        assigneeId = id;
        status = TicketStatus.ASSIGNED;
    }

    public void start() {
        status = TicketStatus.IN_PROGRESS;
        if (startedAt == null) startedAt = Instant.now();
    }

    public void waitParts() {
        status = TicketStatus.WAITING_PARTS;
    }

    public void resolve(String summary) {
        status = TicketStatus.RESOLVED;
        resolutionSummary = summary;
        resolvedAt = Instant.now();
    }

    public void reopen() {
        status = TicketStatus.IN_PROGRESS;
        resolvedAt = null;
        closedAt = null;
    }

    public void close() {
        status = TicketStatus.CLOSED;
        closedAt = Instant.now();
    }

    public void submitFeedback(Integer rating, String comment) {
        if (feedbackSubmittedAt != null) throw new IllegalStateException(
            "Phản hồi của phiếu đã được ghi nhận"
        );
        if (rating != null && (rating < 1 || rating > 5)) throw new IllegalArgumentException(
            "Điểm đánh giá phải từ 1 đến 5"
        );
        String normalizedComment = comment == null || comment.isBlank() ? null : comment.trim();
        if (normalizedComment != null && normalizedComment.length() > 5000) throw new IllegalArgumentException(
            "Nội dung phản hồi không được vượt quá 5000 ký tự"
        );
        if (rating == null && normalizedComment == null) return;
        feedbackRating = rating;
        feedbackComment = normalizedComment;
        feedbackSubmittedAt = Instant.now();
    }

    public String getCode() {
        return code;
    }

    public UUID getEquipmentId() {
        return equipmentId;
    }

    public UUID getRequesterId() {
        return requesterId;
    }

    public UUID getCustomerId() { return customerId; }
    public UUID getSiteId() { return siteId; }
    public UUID getCreatedById(){return createdById;}
    public String getCreationIdempotencyKey(){return creationIdempotencyKey;}
    public UUID getCreationActorId(){return creationActorId;}
    public String getCreationRequestHash(){return creationRequestHash;}
    public WarrantyStatus getWarrantyStatusAtCreation(){return warrantyStatusAtCreation;}
    public Instant getWarrantyEndAtCreation(){return warrantyEndAtCreation;}
    @Deprecated public UUID getBranchId(){return customerId;}
    @Deprecated public UUID getDepartmentId(){return siteId;}

    public UUID getAssigneeId() {
        return assigneeId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getResponseDueAt() {
        return responseDueAt;
    }

    public Instant getResolutionDueAt() {
        return resolutionDueAt;
    }

    public Instant getResolutionWarningAt() {
        return resolutionWarningAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public String getResolutionSummary() {
        return resolutionSummary;
    }

    public Integer getFeedbackRating() {
        return feedbackRating;
    }

    public String getFeedbackComment() {
        return feedbackComment;
    }

    public Instant getFeedbackSubmittedAt() {
        return feedbackSubmittedAt;
    }

}
