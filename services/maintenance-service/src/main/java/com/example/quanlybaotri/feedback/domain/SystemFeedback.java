package com.example.quanlybaotri.feedback.domain;

import com.example.quanlybaotri.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "system_feedback")
public class SystemFeedback extends BaseEntity {

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SystemFeedbackCategory category;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SystemFeedbackStatus status;

    @Column(columnDefinition = "text")
    private String response;

    @Column(name = "reviewed_by_id")
    private UUID reviewedById;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected SystemFeedback() {}

    public SystemFeedback(
        UUID requesterId,
        SystemFeedbackCategory category,
        String title,
        String content
    ) {
        this.requesterId = requesterId;
        this.category = category;
        this.title = required(title, 200, "Tiêu đề phản hồi");
        this.content = required(content, 5000, "Nội dung phản hồi");
        status = SystemFeedbackStatus.NEW;
    }

    public void review(SystemFeedbackStatus target, String value, UUID reviewerId) {
        if (status == SystemFeedbackStatus.RESOLVED || status == SystemFeedbackStatus.REJECTED) {
            throw new IllegalStateException("Phản hồi đã kết thúc xử lý");
        }
        if (target == null || target == SystemFeedbackStatus.NEW) {
            throw new IllegalArgumentException("Trạng thái xử lý không hợp lệ");
        }
        if (status == SystemFeedbackStatus.IN_PROGRESS && target == SystemFeedbackStatus.IN_PROGRESS) {
            throw new IllegalStateException("Phản hồi đang được xử lý");
        }
        String normalized = optional(value, 5000, "Câu trả lời");
        if (
            (target == SystemFeedbackStatus.RESOLVED || target == SystemFeedbackStatus.REJECTED) &&
            normalized == null
        ) throw new IllegalArgumentException("Vui lòng nhập câu trả lời cho requester");
        status = target;
        response = normalized;
        reviewedById = reviewerId;
        reviewedAt = Instant.now();
    }

    private static String required(String value, int max, String label) {
        String normalized = optional(value, max, label);
        if (normalized == null) throw new IllegalArgumentException(label + " không được để trống");
        return normalized;
    }

    private static String optional(String value, int max, String label) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(
            label + " không được vượt quá " + max + " ký tự"
        );
        return normalized;
    }

    public UUID getRequesterId() { return requesterId; }
    public SystemFeedbackCategory getCategory() { return category; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public SystemFeedbackStatus getStatus() { return status; }
    public String getResponse() { return response; }
    public UUID getReviewedById() { return reviewedById; }
    public Instant getReviewedAt() { return reviewedAt; }
}
