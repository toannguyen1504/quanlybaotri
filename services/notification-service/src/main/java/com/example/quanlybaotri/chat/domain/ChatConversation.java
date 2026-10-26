package com.example.quanlybaotri.chat.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_conversations")
public class ChatConversation {
    @Id private UUID id;
    @Column(name = "ticket_id", nullable = false, unique = true) private UUID ticketId;
    @Column(name = "ticket_code", nullable = false, length = 30) private String ticketCode;
    @Column(name = "ticket_title", nullable = false, length = 200) private String ticketTitle;
    @Column(name = "ticket_status", nullable = false, length = 30) private String ticketStatus;
    @Column(name = "requester_id", nullable = false) private UUID requesterId;
    @Column(name = "assignee_id") private UUID assigneeId;
    @Column(name = "last_message_sequence") private Long lastMessageSequence;
    @Column(name = "last_message_preview", length = 240) private String lastMessagePreview;
    @Column(name = "last_message_at") private Instant lastMessageAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected ChatConversation() {}

    public ChatConversation(UUID ticketId, String code, String title, String status, UUID requesterId, UUID assigneeId) {
        this.id = UUID.randomUUID();
        this.ticketId = ticketId;
        updateContext(code, title, status, requesterId, assigneeId);
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void updateContext(String code, String title, String status, UUID requesterId, UUID assigneeId) {
        this.ticketCode = code;
        this.ticketTitle = title;
        this.ticketStatus = status;
        this.requesterId = requesterId;
        this.assigneeId = assigneeId;
        this.updatedAt = Instant.now();
    }

    public void recordMessage(long sequence, String content, Instant at) {
        this.lastMessageSequence = sequence;
        String normalized = content.replaceAll("\\s+", " ").trim();
        this.lastMessagePreview = normalized.substring(0, Math.min(240, normalized.length()));
        this.lastMessageAt = at;
        this.updatedAt = at;
    }

    public UUID getId() { return id; }
    public UUID getTicketId() { return ticketId; }
    public String getTicketCode() { return ticketCode; }
    public String getTicketTitle() { return ticketTitle; }
    public String getTicketStatus() { return ticketStatus; }
    public UUID getRequesterId() { return requesterId; }
    public UUID getAssigneeId() { return assigneeId; }
    public Long getLastMessageSequence() { return lastMessageSequence; }
    public String getLastMessagePreview() { return lastMessagePreview; }
    public Instant getLastMessageAt() { return lastMessageAt; }
}
