package com.example.quanlybaotri.telegram.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "telegram_command_receipts")
public class TelegramCommandReceipt {
    @Id
    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(nullable = false, length = 60)
    private String operation;

    @Column(name = "result_id")
    private UUID resultId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TelegramCommandReceipt() {}

    public TelegramCommandReceipt(String idempotencyKey, String operation) {
        this.idempotencyKey = idempotencyKey;
        this.operation = operation;
        createdAt = Instant.now();
    }

    public void complete(UUID resultId) { this.resultId = resultId; }
    public String getOperation() { return operation; }
    public UUID getResultId() { return resultId; }
}
