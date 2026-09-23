package com.example.quanlybaotri.telegram.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "telegram_links")
public class TelegramLink {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "telegram_user_id", nullable = false, unique = true)
    private long telegramUserId;

    @Column(name = "telegram_chat_id", nullable = false, unique = true)
    private long telegramChatId;

    @Column(name = "telegram_username", length = 64)
    private String telegramUsername;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TelegramLink() {}

    public TelegramLink(
        UUID userId,
        long telegramUserId,
        long telegramChatId,
        String telegramUsername
    ) {
        this.userId = userId;
        reconnect(telegramUserId, telegramChatId, telegramUsername);
        linkedAt = updatedAt;
    }

    public void reconnect(long telegramUserId, long telegramChatId, String telegramUsername) {
        this.telegramUserId = telegramUserId;
        this.telegramChatId = telegramChatId;
        this.telegramUsername = telegramUsername;
        active = true;
        updatedAt = Instant.now();
    }

    public void disable() {
        active = false;
        updatedAt = Instant.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public long getTelegramUserId() {
        return telegramUserId;
    }

    public long getTelegramChatId() {
        return telegramChatId;
    }

    public String getTelegramUsername() {
        return telegramUsername;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getLinkedAt() {
        return linkedAt;
    }
}
