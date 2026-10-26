package com.example.quanlybaotri.telegram.domain;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "telegram_conversations")
public class TelegramConversation {
    public enum State {
        MENU,
        LOG_CONTENT, LOG_MINUTES, LOG_CONFIRM,
        WAIT_REASON, WAIT_CONFIRM,
        RESOLVE_SUMMARY, RESOLVE_CONFIRM,
        PART_SEARCH, PART_QUANTITY, PART_CONFIRM,
        COST_CATEGORY, COST_DESCRIPTION, COST_QUANTITY, COST_UNIT, COST_UNIT_COST,
        COST_VENDOR, COST_REFERENCE, COST_CONFIRM,
        ATTACHMENT_TYPE, ATTACHMENT_FILE, ATTACHMENT_DELETE_CONFIRM
    }

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "ticket_id")
    private UUID ticketId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private State state;

    @Column(name = "draft_json", nullable = false, columnDefinition = "text")
    private String draftJson;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected TelegramConversation() {}

    public TelegramConversation(UUID userId, Duration ttl) {
        this.userId = userId;
        state = State.MENU;
        draftJson = "{}";
        touch(ttl);
    }

    public void select(UUID ticketId, Duration ttl) {
        this.ticketId = ticketId;
        menu(ttl);
    }

    public void step(State state, String draftJson, Duration ttl) {
        this.state = state;
        this.draftJson = draftJson == null ? "{}" : draftJson;
        touch(ttl);
    }

    public void menu(Duration ttl) {
        state = State.MENU;
        draftJson = "{}";
        touch(ttl);
    }

    public void touch(Duration ttl) {
        updatedAt = Instant.now();
        expiresAt = updatedAt.plus(ttl);
    }

    public boolean expired() { return !expiresAt.isAfter(Instant.now()); }
    public UUID getUserId() { return userId; }
    public UUID getTicketId() { return ticketId; }
    public State getState() { return state; }
    public String getDraftJson() { return draftJson; }
    public Instant getExpiresAt() { return expiresAt; }
}
