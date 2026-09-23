package com.example.quanlybaotri.telegram.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "telegram_deliveries")
public class TelegramDelivery {

    public enum Status {
        PENDING,
        SENT,
        FAILED,
        CANCELLED,
    }

    @Id
    private UUID id;

    @Column(name = "notification_id", nullable = false, unique = true)
    private UUID notificationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "message_text", nullable = false, length = 2000)
    private String messageText;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "telegram_message_id")
    private Long telegramMessageId;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected TelegramDelivery() {}

    public TelegramDelivery(
        UUID notificationId,
        UUID userId,
        String messageText,
        UUID referenceId
    ) {
        id = UUID.randomUUID();
        this.notificationId = notificationId;
        this.userId = userId;
        this.messageText = messageText;
        this.referenceId = referenceId;
        status = Status.PENDING;
        nextAttemptAt = Instant.now();
        createdAt = nextAttemptAt;
        updatedAt = nextAttemptAt;
    }

    public void sent(long messageId) {
        status = Status.SENT;
        telegramMessageId = messageId;
        sentAt = Instant.now();
        updatedAt = sentAt;
        lastError = null;
    }

    public void retry(String error, Instant nextAttempt) {
        attempts++;
        lastError = truncate(error);
        nextAttemptAt = nextAttempt;
        updatedAt = Instant.now();
    }

    public void fail(String error) {
        attempts++;
        status = Status.FAILED;
        lastError = truncate(error);
        updatedAt = Instant.now();
    }

    public void cancel(String reason) {
        status = Status.CANCELLED;
        lastError = truncate(reason);
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getMessageText() {
        return messageText;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public int getAttempts() {
        return attempts;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    private static String truncate(String value) {
        if (value == null || value.isBlank()) return "Telegram request failed";
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
